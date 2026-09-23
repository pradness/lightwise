package com.lightwise.usage_service.dto;

import java.util.List;

import lombok.Builder;

@Builder
/**
 * UsageDto
 */
public record UsageDto(
    Long userId,
    List<DeviceDto> devices) {
}
