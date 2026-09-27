package com.lightwise.agent_service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;
import com.lightwise.agent_service.service.AgentDecisionService;

@WebMvcTest(AgentDecisionController.class)
class AgentDecisionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AgentDecisionService agentDecisionService;

  @Test
  void testApproveDecision() throws Exception {
    AgentDecisionLog executed = AgentDecisionLog.builder()
        .id(1L)
        .deviceId(10L)
        .proposedAction(ProposedAction.SHUTOFF)
        .reasoning("High consumption")
        .status(DecisionStatus.EXECUTED)
        .requestedAt(Instant.now())
        .resolvedAt(Instant.now())
        .build();

    when(agentDecisionService.approveDecision(1L)).thenReturn(executed);

    mockMvc.perform(post("/agent/decisions/1/approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("EXECUTED"))
        .andExpect(jsonPath("$.proposedAction").value("SHUTOFF"));

    mockMvc.perform(post("/api/v1/agent/decisions/1/approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXECUTED"));
  }

  @Test
  void testRejectDecision() throws Exception {
    AgentDecisionLog rejected = AgentDecisionLog.builder()
        .id(2L)
        .deviceId(20L)
        .proposedAction(ProposedAction.RESUME)
        .reasoning("Normal pattern")
        .status(DecisionStatus.REJECTED)
        .requestedAt(Instant.now())
        .resolvedAt(Instant.now())
        .build();

    when(agentDecisionService.rejectDecision(2L)).thenReturn(rejected);

    mockMvc.perform(post("/agent/decisions/2/reject"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  void testGetDecisions() throws Exception {
    AgentDecisionLog log1 = AgentDecisionLog.builder()
        .id(1L)
        .deviceId(10L)
        .proposedAction(ProposedAction.SHUTOFF)
        .status(DecisionStatus.PENDING_APPROVAL)
        .requestedAt(Instant.now())
        .build();

    when(agentDecisionService.getDecisions(DecisionStatus.PENDING_APPROVAL)).thenReturn(List.of(log1));

    mockMvc.perform(get("/agent/decisions").param("status", "PENDING_APPROVAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
  }
}
