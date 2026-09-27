package com.lightwise.agent_service.tools;

import java.time.Instant;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.lightwise.agent_service.client.DeviceClient;
import com.lightwise.agent_service.client.UsageClient;
import com.lightwise.agent_service.dto.DeviceResponse;
import com.lightwise.agent_service.dto.DeviceUsageHistoryResponse;
import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;
import com.lightwise.agent_service.repository.AgentDecisionLogRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AgentTools {

  private final UsageClient usageClient;
  private final DeviceClient deviceClient;
  private final AgentDecisionLogRepository decisionLogRepository;

  public AgentTools(UsageClient usageClient, DeviceClient deviceClient, AgentDecisionLogRepository decisionLogRepository) {
    this.usageClient = usageClient;
    this.deviceClient = deviceClient;
    this.decisionLogRepository = decisionLogRepository;
  }

  @Tool(description = "Get usage history for a device over a specified time window (e.g. '1h', '24h')")
  public DeviceUsageHistoryResponse getUsageHistory(
      @ToolParam(description = "ID of the device") Long deviceId,
      @ToolParam(description = "Time window such as '1h' or '24h'") String window) {
    log.info("Tool getUsageHistory called for deviceId: {}, window: {}", deviceId, window);
    return usageClient.getUsageHistory(deviceId, window);
  }

  @Tool(description = "Get device information and configuration for a device by its ID")
  public DeviceResponse getDeviceInfo(
      @ToolParam(description = "ID of the device") Long deviceId) {
    log.info("Tool getDeviceInfo called for deviceId: {}", deviceId);
    return deviceClient.getDeviceInfo(deviceId);
  }

  @Tool(description = "Propose an action (SHUTOFF or RESUME) for human approval. Does not perform the action directly.")
  public String proposeAction(
      @ToolParam(description = "ID of the device") Long deviceId,
      @ToolParam(description = "Proposed action: SHUTOFF or RESUME") String action,
      @ToolParam(description = "Reasoning justifying why this action is proposed") String reasoning) {
    log.info("Tool proposeAction called for deviceId: {}, action: {}, reasoning: {}", deviceId, action, reasoning);

    ProposedAction proposedAction = ProposedAction.valueOf(action.toUpperCase().trim());
    AgentDecisionLog decisionLog = AgentDecisionLog.builder()
        .deviceId(deviceId)
        .proposedAction(proposedAction)
        .reasoning(reasoning)
        .status(DecisionStatus.PENDING_APPROVAL)
        .requestedAt(Instant.now())
        .build();

    decisionLog = decisionLogRepository.save(decisionLog);

    log.info("NOTIFY: propose {} for device {}, reasoning: {}", proposedAction, deviceId, reasoning);
    return "Action " + proposedAction + " proposed with ID " + decisionLog.getId() + " and logged for approval.";
  }
}
