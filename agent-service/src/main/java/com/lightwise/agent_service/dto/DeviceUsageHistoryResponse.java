package com.lightwise.agent_service.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceUsageHistoryResponse {
  private Long deviceId;
  private String window;
  private Double totalEnergyConsumed;
  private List<DataPoint> dataPoints;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DataPoint {
    private Instant timestamp;
    private Double energyConsumed;
  }
}
