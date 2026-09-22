package com.lightwise.alert_service.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.lightwise.kafka.event.AlertingEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertService {

  private final EmailService emailService;

  public AlertService(EmailService emailService) {
    this.emailService = emailService;
  }

  @KafkaListener(topics = "energy-alerts", groupId = "alert_service")
  public void energyUsageAlertEvent(AlertingEvent alertingEvent) {
    log.info("Recieved alert event: {}", alertingEvent);

    // Send email alert
    final String subject = "Email Usage Alert for User " + alertingEvent.getUserId();
    final String message = "Alert" + alertingEvent.getMessage() +
        "\nThreshold: " + alertingEvent.getThreshold() +
        "\nEnergy Consumed: " + alertingEvent.getEnergyConsumed();
    emailService.sendEmail(
        alertingEvent.getEmail(),
        subject,
        message,
        alertingEvent.getUserId());

  }
}
