package com.lightwise.ingestion_service.simulation;

import com.lightwise.ingestion_service.dto.EnergyUsageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;

@Slf4j
@Component
public class ContinuousDataSimulator implements CommandLineRunner {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();
    @Value("${simulation.requests-per-interval}")
    private int requestsPerInterval;
    @Value("${simulation.endpoint}")
    private String ingestionEndpoint;

    @Override
    public void run(String... args) throws Exception {
        log.info("Continuous Data Simulator started...");
    }

    //@Scheduled(fixedRateString = "${simulation.interval-ms}")
    public void sendMockData(){
        ParallelDataSimulator.DataSimulator(requestsPerInterval, random, restTemplate, ingestionEndpoint, log);
    }

}
