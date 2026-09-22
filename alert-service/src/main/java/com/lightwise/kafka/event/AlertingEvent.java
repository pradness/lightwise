package com.lightwise.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertingEvent {
  Long userId;
  String message;
  double threshold;
  double energyConsumed;
  String email;
}
