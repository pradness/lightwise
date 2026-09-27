package com.lightwise.agent_service.entity;

import java.time.Instant;

import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_decision_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDecisionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "proposed_action", nullable = false, length = 20)
  private ProposedAction proposedAction;

  @Column(columnDefinition = "TEXT")
  private String reasoning;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DecisionStatus status;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public AgentDecisionLog(Long deviceId, ProposedAction proposedAction, String reasoning, DecisionStatus status) {
    this.deviceId = deviceId;
    this.proposedAction = proposedAction;
    this.reasoning = reasoning;
    this.status = status;
    this.requestedAt = Instant.now();
  }
}
