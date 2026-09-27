package com.lightwise.agent_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;

@Repository
public interface AgentDecisionLogRepository extends JpaRepository<AgentDecisionLog, Long> {
  List<AgentDecisionLog> findByStatus(DecisionStatus status);
}
