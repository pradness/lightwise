package com.lightwise.insight_service.dto;

import lombok.Builder;

@Builder
/**
 * DeviceDto
 */
public record DeviceDto(
    Long id,
    String name,
    String type,
    String location,
    double energyConsumed) {
}
