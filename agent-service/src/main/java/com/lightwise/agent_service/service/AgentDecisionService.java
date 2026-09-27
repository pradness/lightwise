package com.lightwise.agent_service.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.lightwise.agent_service.client.DeviceClient;
import com.lightwise.agent_service.dto.CommandRequest;
import com.lightwise.agent_service.dto.DeviceResponse;
import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.repository.AgentDecisionLogRepository;
import com.lightwise.agent_service.tools.AgentTools;
import com.lightwise.kafka.event.AlertingEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentDecisionService {

  public static final String SYSTEM_PROMPT =
      "You cannot shut off or resume any device yourself. You can only propose an action via proposeAction, "
      + "which requires human approval before anything happens. Only propose SHUTOFF for genuine sustained overuse, "
      + "not normal cycling patterns (e.g. fridge compressors, HVAC cycling).";

  private final ChatClient chatClient;
  private final DeviceClient deviceClient;
  private final AgentDecisionLogRepository decisionLogRepository;
  private final List<AlertingEvent> receivedEvents = new CopyOnWriteArrayList<>();

  public AgentDecisionService(
      ChatClient.Builder chatClientBuilder,
      AgentTools agentTools,
      DeviceClient deviceClient,
      AgentDecisionLogRepository decisionLogRepository) {
    this.deviceClient = deviceClient;
    this.decisionLogRepository = decisionLogRepository;
    this.chatClient = chatClientBuilder
        .defaultSystem(SYSTEM_PROMPT)
        .defaultTools(agentTools)
        .build();
  }

  public void evaluate(AlertingEvent event) {
    receivedEvents.add(event);
    log.info("Evaluating energy alert event: userId={}, message={}, threshold={}, energyConsumed={}, email={}",
        event.getUserId(), event.getMessage(), event.getThreshold(), event.getEnergyConsumed(), event.getEmail());

    List<DeviceResponse> devices = deviceClient.getDevicesByUserId(event.getUserId());
    List<DeviceResponse> candidateDevices = (devices != null && !devices.isEmpty())
        ? devices.stream().limit(2).toList()
        : List.of();

    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append("An energy threshold breach alert has been triggered for user ID ")
        .append(event.getUserId())
        .append(". Alert message: '").append(event.getMessage())
        .append("'. Total energy consumed: ").append(event.getEnergyConsumed())
        .append(" W/Wh, exceeding threshold of ").append(event.getThreshold()).append(" W/Wh.\n");

    if (!candidateDevices.isEmpty()) {
      promptBuilder.append("The user has the following candidate device(s):\n");
      for (DeviceResponse dev : candidateDevices) {
        promptBuilder.append("- Device ID: ").append(dev.getId())
            .append(", Name: ").append(dev.getName())
            .append(", Type: ").append(dev.getType())
            .append(", Location: ").append(dev.getLocation()).append("\n");
      }
    } else {
      promptBuilder.append("No registered devices could be retrieved directly for user ID ")
          .append(event.getUserId()).append(".\n");
    }

    promptBuilder.append("\nPlease investigate the candidate device(s) by checking device info and usage history. "
        + "Evaluate if there is genuine sustained overuse vs normal cycling patterns. "
        + "If you determine there is genuine sustained overuse, propose an appropriate action using the proposeAction tool.");

    try {
      String response = chatClient.prompt()
          .user(promptBuilder.toString())
          .call()
          .content();
      log.info("LLM evaluation response for user {}: {}", event.getUserId(), response);
    } catch (Exception e) {
      log.error("Error during LLM evaluation for user {}: {}", event.getUserId(), e.getMessage(), e);
    }
  }

  public AgentDecisionLog approveDecision(Long id) {
    AgentDecisionLog decision = decisionLogRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found with id " + id));

    if (decision.getStatus() != DecisionStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Decision is not pending approval (current status: " + decision.getStatus() + ")");
    }

    CommandRequest request = new CommandRequest(decision.getProposedAction().name());
    ResponseEntity<Void> response = deviceClient.sendCommand(decision.getDeviceId(), request);

    if (response.getStatusCode().is2xxSuccessful()) {
      decision.setStatus(DecisionStatus.EXECUTED);
    } else if (response.getStatusCode().value() == 409) {
      decision.setStatus(DecisionStatus.BLOCKED);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Device service returned unexpected status: " + response.getStatusCode());
    }

    decision.setResolvedAt(Instant.now());
    return decisionLogRepository.save(decision);
  }

  public AgentDecisionLog rejectDecision(Long id) {
    AgentDecisionLog decision = decisionLogRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Decision not found with id " + id));

    if (decision.getStatus() != DecisionStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Decision is not pending approval (current status: " + decision.getStatus() + ")");
    }

    decision.setStatus(DecisionStatus.REJECTED);
    decision.setResolvedAt(Instant.now());
    return decisionLogRepository.save(decision);
  }

  public List<AgentDecisionLog> getDecisions(DecisionStatus status) {
    if (status != null) {
      return decisionLogRepository.findByStatus(status);
    }
    return decisionLogRepository.findAll();
  }

  public List<AlertingEvent> getReceivedEvents() {
    return receivedEvents;
  }
}
