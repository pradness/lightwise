package com.lightwise.agent_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lightwise.agent_service.dto.CommandRequest;
import com.lightwise.agent_service.dto.DeviceResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DeviceClient {

  private final RestClient restClient;

  public DeviceClient(@Value("${device.service.url:http://localhost:8081/api/v1/device}") String deviceServiceUrl) {
    this.restClient = RestClient.builder()
        .baseUrl(deviceServiceUrl)
        .build();
  }

  public DeviceResponse getDeviceInfo(Long deviceId) {
    log.info("Fetching device info for device {}", deviceId);
    return restClient.get()
        .uri("/{id}", deviceId)
        .retrieve()
        .body(DeviceResponse.class);
  }

  public ResponseEntity<Void> sendCommand(Long deviceId, CommandRequest request) {
    log.info("Sending command {} to device {}", request.getAction(), deviceId);
    return restClient.post()
        .uri("/{id}/command", deviceId)
        .body(request)
        .exchange((clientReq, clientResp) ->
            ResponseEntity.status(clientResp.getStatusCode()).build()
        );
  }

  public java.util.List<DeviceResponse> getDevicesByUserId(Long userId) {
    try {
      log.info("Fetching devices for userId {}", userId);
      return restClient.get()
          .uri("/user/{userId}", userId)
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<DeviceResponse>>() {});
    } catch (Exception e) {
      log.warn("Failed to get devices for userId {}: {}", userId, e.getMessage());
      return java.util.Collections.emptyList();
    }
  }
}
