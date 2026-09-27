package com.lightwise.agent_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.service.AgentDecisionService;

@RestController
@RequestMapping({"/agent/decisions", "/api/v1/agent/decisions"})
public class AgentDecisionController {

  private final AgentDecisionService agentDecisionService;

  public AgentDecisionController(AgentDecisionService agentDecisionService) {
    this.agentDecisionService = agentDecisionService;
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<AgentDecisionLog> approveDecision(@PathVariable Long id) {
    AgentDecisionLog decision = agentDecisionService.approveDecision(id);
    return ResponseEntity.ok(decision);
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<AgentDecisionLog> rejectDecision(@PathVariable Long id) {
    AgentDecisionLog decision = agentDecisionService.rejectDecision(id);
    return ResponseEntity.ok(decision);
  }

  @GetMapping
  public ResponseEntity<List<AgentDecisionLog>> getDecisions(
      @RequestParam(required = false) DecisionStatus status) {
    List<AgentDecisionLog> decisions = agentDecisionService.getDecisions(status);
    return ResponseEntity.ok(decisions);
  }
}
