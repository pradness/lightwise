package com.lightwise.insight_service.dto;

import lombok.Builder;

@Builder
/**
 * InsightDto
 */
public record InsightDto(
    Long userId,
    String tips,
    double energyUsage) {
}
