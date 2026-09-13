package com.lightwise.usage_service.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.lightwise.kafka.event.AlertingEvent;
import com.lightwise.kafka.event.EnergyUsageEvent;
import com.lightwise.usage_service.client.DeviceClient;
import com.lightwise.usage_service.client.UserClient;
import com.lightwise.usage_service.dto.DeviceDto;
import com.lightwise.usage_service.dto.UserDto;
import com.lightwise.usage_service.model.DeviceEnergy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UsageService {

    private static final String MEASUREMENT_ENERGY_USAGE = "energy-usage";

    private final InfluxDBClient influxDBClient;
    private final DeviceClient deviceClient;
    private final UserClient userClient;

    @Value("${influx.bucket}")
    private String influxBucket;

    @Value("${influx.org}")
    private String influxOrg;

    private final KafkaTemplate<String, AlertingEvent> kafkaTemplate;

    public UsageService(InfluxDBClient influxDBClient,
                        DeviceClient deviceClient,
                        UserClient userClient,
                        KafkaTemplate<String, AlertingEvent> kafkaTemplate) {
        this.influxDBClient = influxDBClient;
        this.deviceClient = deviceClient;
        this.userClient = userClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "energy-usage", groupId = "usage-service")
    public void energyUsageEvent(EnergyUsageEvent energyUsageEvent) {
        // log.info("Received energy usage event: {}", energyUsageEvent);
        Point point = Point.measurement(MEASUREMENT_ENERGY_USAGE)
                .addTag("deviceId", String.valueOf(energyUsageEvent.deviceId()))
                .addField("energyConsumed", energyUsageEvent.energyConsumed())
                .time(energyUsageEvent.timestamp(), WritePrecision.MS);
        influxDBClient.getWriteApiBlocking().writePoint(influxBucket, influxOrg, point);
    }

    @Scheduled(cron = "*/10 * * * * *")
    public void aggregateDeviceEnergyUsage() {

        List<DeviceEnergy> deviceEnergies = getDeviceEnergies();

        Map<Long, List<DeviceEnergy>> userDeviceEnergyMap = getUserDeviceEnergyMap(deviceEnergies);

        UserMaps userMaps = getUserThresholdMapAndUserEmailMap(userDeviceEnergyMap);

        alerting(userDeviceEnergyMap, userMaps);

    }

    private void alerting(Map<Long, List<DeviceEnergy>> userDeviceEnergyMap, UserMaps userMaps) {
        final Set<Long> activeUserIds = userMaps.userThresholdMap().keySet();
        for (final Long userId : activeUserIds) {
            final Double thresholdFromMap = userMaps.userThresholdMap().get(userId);
            final Double threshold = (thresholdFromMap != null) ? thresholdFromMap : 0.0;

            final List<DeviceEnergy> devices = userDeviceEnergyMap.get(userId);

            final Double totalConsumption = devices.stream()
                    .mapToDouble(DeviceEnergy::getEnergyConsumed)
                    .sum();

            if (totalConsumption > threshold) {
                log.info("ALERT: User ID {} has exceeded threshold! "+
                        "Total Consumption: {}, Threshold: {}",
                        userId, totalConsumption, threshold);

                final AlertingEvent alertingEvent = AlertingEvent.builder()
                        .userId(userId)
                        .message("Energy consumption threshold exceeded")
                        .threshold(threshold)
                        .energyConsumed(totalConsumption)
                        .email(userMaps.userEmailMap().get(userId))
                        .build();

                kafkaTemplate.send("energy-alerts", alertingEvent);
            } else {
                log.info("User ID {} is within the energy threshold. " +
                        "Total Consumption: {}, Threshold: {}",
                        userId, totalConsumption, threshold
                );
            }
        }
    }

    private @NonNull UserMaps getUserThresholdMapAndUserEmailMap(Map<Long, List<DeviceEnergy>> userDeviceEnergyMap) {
        List<Long> userIds = new ArrayList<>(userDeviceEnergyMap.keySet());
        final Map<Long, Double> userThresholdMap = new HashMap<>();
        final Map<Long, String> userEmailMap = new HashMap<>();

        for (final Long  userId : userIds) {
            try {
                UserDto user = userClient.getUserById(userId);
                if (user == null || user.id() == null || !user.alerting()) {
                    log.warn("User not found or alerting disabled for ID: {}", userId);
                    continue;
                }
                userThresholdMap.put(user.id(), user.energyAlertingThreshold());
                userEmailMap.put(user.id(), user.email());
            } catch (Exception e) {
                log.warn("Error occurred while getting user threshold for user with id {}", userId, e);
            }
        }
        log.info("User-Threshold Map: {}", userThresholdMap);
        return new UserMaps(userThresholdMap, userEmailMap);
    }

    private record UserMaps(Map<Long, Double> userThresholdMap, Map<Long, String> userEmailMap) {
    }

    private @NonNull Map<Long, List<DeviceEnergy>> getUserDeviceEnergyMap(List<DeviceEnergy> deviceEnergies) {
        for (DeviceEnergy deviceEnergy : deviceEnergies){
            try{
                final DeviceDto deviceResponse = deviceClient.getDeviceById(deviceEnergy.getDeviceId());
                if (deviceResponse == null || deviceResponse.id() == null) {
                    log.warn("No device with id {} found", deviceEnergy.getDeviceId());
                    continue;
                }
                deviceEnergy.setUserId(deviceResponse.userId());
            } catch (Exception e) {
                log.warn("Failed to get user device with id {}", deviceEnergy.getDeviceId(), e);
            }
        }

        deviceEnergies.removeIf(de -> de.getUserId() == null);

        Map<Long, List<DeviceEnergy>> userDeviceEnergyMap = deviceEnergies.stream()
                .collect(Collectors.groupingBy(DeviceEnergy::getUserId));

        log.info("User-Device Energy Map: {}", userDeviceEnergyMap);
        return userDeviceEnergyMap;
    }

    private @NonNull List<DeviceEnergy> getDeviceEnergies() {
        final Instant now = Instant.now();
        final Instant oneHourAgo = now.minusSeconds(3600);

        String fluxQuery = String.format("""
        from(bucket: "%s")
          |> range(start: time(v: "%s"), stop: time(v: "%s"))
          |> filter(fn: (r) => r["_measurement"] == "%s")
          |> filter(fn: (r) => r["_field"] == "energyConsumed")
          |> group(columns: ["deviceId"])
          |> sum(column: "_value")
        """, influxBucket, oneHourAgo.toString(), now.toString(), MEASUREMENT_ENERGY_USAGE);

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);

        List<DeviceEnergy> deviceEnergies = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                String deviceIdStr = (String) record.getValueByKey("deviceId");
                double energyConsumed = record.getValueByKey("_value") instanceof Number ?
                        ((Number) Objects.requireNonNull(record.getValueByKey("_value"))).doubleValue() : 0.0;
                assert deviceIdStr != null;
                deviceEnergies.add(
                        DeviceEnergy.builder()
                                .deviceId(Long.valueOf(deviceIdStr))
                                .energyConsumed((energyConsumed))
                                .build()
                );

            }
        }
        log.info("Aggregated device energies over the past hour: {}", deviceEnergies);
        return deviceEnergies;
    }
}
