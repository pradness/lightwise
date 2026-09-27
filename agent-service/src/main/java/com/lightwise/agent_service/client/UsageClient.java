package com.lightwise.agent_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lightwise.agent_service.dto.DeviceUsageHistoryResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UsageClient {

  private final RestClient restClient;

  public UsageClient(@Value("${usage.service.url:http://localhost:8083/api/v1/usage}") String usageServiceUrl) {
    this.restClient = RestClient.builder()
        .baseUrl(usageServiceUrl)
        .build();
  }

  public DeviceUsageHistoryResponse getUsageHistory(Long deviceId, String window) {
    log.info("Fetching usage history for device {} with window {}", deviceId, window);
    return restClient.get()
        .uri("/{deviceId}/history?window={window}", deviceId, window != null ? window : "1h")
        .retrieve()
        .body(DeviceUsageHistoryResponse.class);
  }
}
