package com.lightwise.device_service.dto;

import com.lightwise.device_service.model.DeviceAction;

import jakarta.validation.constraints.NotNull;

public record CommandRequest(@NotNull DeviceAction action) {
}
