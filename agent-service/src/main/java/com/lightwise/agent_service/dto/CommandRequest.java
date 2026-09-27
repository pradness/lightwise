package com.lightwise.agent_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandRequest {
  private String action; // "SHUTOFF" or "RESUME"
}
