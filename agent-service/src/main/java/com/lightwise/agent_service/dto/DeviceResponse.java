package com.lightwise.agent_service.dto;

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
public class DeviceResponse {
  private Long id;
  private String name;
  private String type;
  private String location;
  private Long userId;
  private Boolean isOn;
  private Boolean neverShutOff;
}
