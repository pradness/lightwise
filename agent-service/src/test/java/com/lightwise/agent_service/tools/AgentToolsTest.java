package com.lightwise.agent_service.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lightwise.agent_service.client.DeviceClient;
import com.lightwise.agent_service.client.UsageClient;
import com.lightwise.agent_service.dto.DeviceResponse;
import com.lightwise.agent_service.dto.DeviceUsageHistoryResponse;
import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;
import com.lightwise.agent_service.repository.AgentDecisionLogRepository;

@ExtendWith(MockitoExtension.class)
class AgentToolsTest {

  @Mock
  private UsageClient usageClient;

  @Mock
  private DeviceClient deviceClient;

  @Mock
  private AgentDecisionLogRepository repository;

  private AgentTools agentTools;

  @BeforeEach
  void setUp() {
    agentTools = new AgentTools(usageClient, deviceClient, repository);
  }

  @Test
  void testGetUsageHistory() {
    DeviceUsageHistoryResponse mockResponse = DeviceUsageHistoryResponse.builder()
        .deviceId(1L)
        .window("1h")
        .totalEnergyConsumed(250.0)
        .build();

    when(usageClient.getUsageHistory(1L, "1h")).thenReturn(mockResponse);

    DeviceUsageHistoryResponse result = agentTools.getUsageHistory(1L, "1h");
    assertNotNull(result);
    assertEquals(1L, result.getDeviceId());
    assertEquals(250.0, result.getTotalEnergyConsumed());
    verify(usageClient).getUsageHistory(1L, "1h");
  }

  @Test
  void testGetDeviceInfo() {
    DeviceResponse mockResponse = DeviceResponse.builder()
        .id(1L)
        .name("Refrigerator")
        .type("REFRIGERATOR")
        .isOn(true)
        .neverShutOff(true)
        .build();

    when(deviceClient.getDeviceInfo(1L)).thenReturn(mockResponse);

    DeviceResponse result = agentTools.getDeviceInfo(1L);
    assertNotNull(result);
    assertEquals("Refrigerator", result.getName());
    assertTrue(result.getNeverShutOff());
    verify(deviceClient).getDeviceInfo(1L);
  }

  @Test
  void testProposeActionDoesNotCallDeviceServiceDirectly() {
    when(repository.save(any(AgentDecisionLog.class))).thenAnswer(invocation -> {
      AgentDecisionLog saved = invocation.getArgument(0);
      saved.setId(42L);
      return saved;
    });

    String result = agentTools.proposeAction(1L, "SHUTOFF", "Sustained high draw detected");

    assertTrue(result.contains("SHUTOFF"));
    assertTrue(result.contains("42"));

    // Verify repository saved entity with PENDING_APPROVAL
    verify(repository).save(argThat(log ->
        log.getDeviceId().equals(1L) &&
        log.getProposedAction() == ProposedAction.SHUTOFF &&
        log.getStatus() == DecisionStatus.PENDING_APPROVAL &&
        "Sustained high draw detected".equals(log.getReasoning())
    ));

    // Verify deviceClient was NEVER called (no auto-execution!)
    verifyNoInteractions(deviceClient);
  }
}
