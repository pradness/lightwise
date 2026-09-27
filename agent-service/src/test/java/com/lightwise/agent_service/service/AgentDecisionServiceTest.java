package com.lightwise.agent_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.lightwise.agent_service.client.DeviceClient;
import com.lightwise.agent_service.dto.CommandRequest;
import com.lightwise.agent_service.dto.DeviceResponse;
import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;
import com.lightwise.agent_service.repository.AgentDecisionLogRepository;
import com.lightwise.agent_service.tools.AgentTools;
import com.lightwise.kafka.event.AlertingEvent;

@ExtendWith(MockitoExtension.class)
class AgentDecisionServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient.Builder chatClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  @Mock
  private AgentTools agentTools;

  @Mock
  private DeviceClient deviceClient;

  @Mock
  private AgentDecisionLogRepository repository;

  private AgentDecisionService agentDecisionService;

  @BeforeEach
  void setUp() {
    when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
    when(chatClientBuilder.defaultTools(any(Object[].class))).thenReturn(chatClientBuilder);
    when(chatClientBuilder.build()).thenReturn(chatClient);

    agentDecisionService = new AgentDecisionService(chatClientBuilder, agentTools, deviceClient, repository);
  }

  @Test
  void testSystemPromptRequirement() {
    verify(chatClientBuilder).defaultSystem(eq(AgentDecisionService.SYSTEM_PROMPT));
    assertTrue(AgentDecisionService.SYSTEM_PROMPT.contains("You cannot shut off or resume any device yourself"));
    assertTrue(AgentDecisionService.SYSTEM_PROMPT.contains("proposeAction"));
    assertTrue(AgentDecisionService.SYSTEM_PROMPT.contains("Only propose SHUTOFF for genuine sustained overuse"));
  }

  @Test
  void testEvaluateCallsDeviceClientAndChatClient() {
    AlertingEvent event = AlertingEvent.builder()
        .userId(1L)
        .message("Energy consumption threshold exceeded")
        .threshold(200.0)
        .energyConsumed(350.0)
        .email("user1@example.com")
        .build();

    DeviceResponse dev1 = DeviceResponse.builder()
        .id(10L)
        .name("Space Heater")
        .type("HEATER")
        .location("Living Room")
        .isOn(true)
        .neverShutOff(false)
        .build();

    when(deviceClient.getDevicesByUserId(1L)).thenReturn(List.of(dev1));
    when(chatClient.prompt().user(anyString()).call().content()).thenReturn("Evaluated: proposed shutoff for device 10");

    agentDecisionService.evaluate(event);

    verify(deviceClient).getDevicesByUserId(1L);
    verify(chatClient.prompt().user(anyString()).call()).content();
    assertEquals(1, agentDecisionService.getReceivedEvents().size());
  }

  @Test
  void testApproveDecisionExecutedOn200() {
    AgentDecisionLog pending = AgentDecisionLog.builder()
        .id(1L)
        .deviceId(10L)
        .proposedAction(ProposedAction.SHUTOFF)
        .reasoning("High consumption")
        .status(DecisionStatus.PENDING_APPROVAL)
        .requestedAt(Instant.now())
        .build();

    when(repository.findById(1L)).thenReturn(Optional.of(pending));
    when(deviceClient.sendCommand(eq(10L), any(CommandRequest.class)))
        .thenReturn(ResponseEntity.ok().build());
    when(repository.save(any(AgentDecisionLog.class))).thenAnswer(i -> i.getArgument(0));

    AgentDecisionLog result = agentDecisionService.approveDecision(1L);

    assertEquals(DecisionStatus.EXECUTED, result.getStatus());
    assertNotNull(result.getResolvedAt());
    verify(deviceClient).sendCommand(eq(10L), argThat(r -> "SHUTOFF".equals(r.getAction())));
    verify(repository).save(pending);
  }

  @Test
  void testApproveDecisionBlockedOn409() {
    AgentDecisionLog pending = AgentDecisionLog.builder()
        .id(2L)
        .deviceId(20L)
        .proposedAction(ProposedAction.SHUTOFF)
        .reasoning("High consumption")
        .status(DecisionStatus.PENDING_APPROVAL)
        .requestedAt(Instant.now())
        .build();

    when(repository.findById(2L)).thenReturn(Optional.of(pending));
    when(deviceClient.sendCommand(eq(20L), any(CommandRequest.class)))
        .thenReturn(ResponseEntity.status(HttpStatus.CONFLICT).build());
    when(repository.save(any(AgentDecisionLog.class))).thenAnswer(i -> i.getArgument(0));

    AgentDecisionLog result = agentDecisionService.approveDecision(2L);

    assertEquals(DecisionStatus.BLOCKED, result.getStatus());
    assertNotNull(result.getResolvedAt());
    verify(deviceClient).sendCommand(eq(20L), argThat(r -> "SHUTOFF".equals(r.getAction())));
    verify(repository).save(pending);
  }

  @Test
  void testRejectDecisionDoesNotCallDeviceService() {
    AgentDecisionLog pending = AgentDecisionLog.builder()
        .id(3L)
        .deviceId(30L)
        .proposedAction(ProposedAction.SHUTOFF)
        .reasoning("High consumption")
        .status(DecisionStatus.PENDING_APPROVAL)
        .requestedAt(Instant.now())
        .build();

    when(repository.findById(3L)).thenReturn(Optional.of(pending));
    when(repository.save(any(AgentDecisionLog.class))).thenAnswer(i -> i.getArgument(0));

    AgentDecisionLog result = agentDecisionService.rejectDecision(3L);

    assertEquals(DecisionStatus.REJECTED, result.getStatus());
    assertNotNull(result.getResolvedAt());
    verifyNoInteractions(deviceClient);
    verify(repository).save(pending);
  }

  @Test
  void testApproveAlreadyResolvedDecisionThrowsBadRequest() {
    AgentDecisionLog resolved = AgentDecisionLog.builder()
        .id(4L)
        .deviceId(40L)
        .proposedAction(ProposedAction.SHUTOFF)
        .reasoning("Already executed")
        .status(DecisionStatus.EXECUTED)
        .requestedAt(Instant.now())
        .resolvedAt(Instant.now())
        .build();

    when(repository.findById(4L)).thenReturn(Optional.of(resolved));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
        agentDecisionService.approveDecision(4L)
    );
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verifyNoInteractions(deviceClient);
  }

  @Test
  void testGetDecisionsWithAndWithoutStatus() {
    AgentDecisionLog log1 = AgentDecisionLog.builder().id(1L).status(DecisionStatus.PENDING_APPROVAL).build();
    AgentDecisionLog log2 = AgentDecisionLog.builder().id(2L).status(DecisionStatus.EXECUTED).build();

    when(repository.findAll()).thenReturn(List.of(log1, log2));
    when(repository.findByStatus(DecisionStatus.PENDING_APPROVAL)).thenReturn(List.of(log1));

    List<AgentDecisionLog> all = agentDecisionService.getDecisions(null);
    assertEquals(2, all.size());

    List<AgentDecisionLog> filtered = agentDecisionService.getDecisions(DecisionStatus.PENDING_APPROVAL);
    assertEquals(1, filtered.size());
    assertEquals(DecisionStatus.PENDING_APPROVAL, filtered.get(0).getStatus());
  }
}
