package com.lightwise.usage_service.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record DeviceUsageHistoryDto(
    Long deviceId,
    String window,
    Double totalEnergyConsumed,
    List<DataPoint> dataPoints
) {
  @Builder
  public record DataPoint(
      Instant timestamp,
      Double energyConsumed
  ) {}
}
