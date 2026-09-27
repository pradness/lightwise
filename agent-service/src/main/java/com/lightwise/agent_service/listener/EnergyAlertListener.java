package com.lightwise.agent_service.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.lightwise.agent_service.service.AgentDecisionService;
import com.lightwise.kafka.event.AlertingEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EnergyAlertListener {

  private final AgentDecisionService agentDecisionService;

  public EnergyAlertListener(AgentDecisionService agentDecisionService) {
    this.agentDecisionService = agentDecisionService;
  }

  @KafkaListener(topics = "energy-alerts", groupId = "agent-service")
  public void consumeAlert(AlertingEvent event) {
    log.info("Received alert event from energy-alerts topic: {}", event);
    agentDecisionService.evaluate(event);
  }
}
