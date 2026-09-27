package com.lightwise.agent_service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.lightwise.agent_service.entity.AgentDecisionLog;
import com.lightwise.agent_service.model.DecisionStatus;
import com.lightwise.agent_service.model.ProposedAction;
import com.lightwise.agent_service.repository.AgentDecisionLogRepository;
import com.lightwise.agent_service.service.AgentDecisionService;
import com.lightwise.kafka.event.AlertingEvent;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(AgentServiceApplicationTests.KafkaTestProducerConfig.class)
class AgentServiceApplicationTests {

	@TestConfiguration
	static class KafkaTestProducerConfig {
		@Bean
		public ProducerFactory<String, AlertingEvent> testProducerFactory() {
			Map<String, Object> configProps = new HashMap<>();
			configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
			configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
			return new DefaultKafkaProducerFactory<>(configProps);
		}

		@Bean
		public KafkaTemplate<String, AlertingEvent> testKafkaTemplate() {
			return new KafkaTemplate<>(testProducerFactory());
		}
	}

	@Autowired
	private AgentDecisionLogRepository repository;

	@Autowired
	private AgentDecisionService agentDecisionService;

	@Autowired
	private KafkaTemplate<String, AlertingEvent> testKafkaTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void testAgentDecisionLogPersistence() {
		AgentDecisionLog log = AgentDecisionLog.builder()
				.deviceId(100L)
				.proposedAction(ProposedAction.SHUTOFF)
				.reasoning("High sustained usage detected")
				.status(DecisionStatus.PENDING_APPROVAL)
				.requestedAt(Instant.now())
				.build();

		AgentDecisionLog saved = repository.save(log);
		assertNotNull(saved.getId());

		List<AgentDecisionLog> pending = repository.findByStatus(DecisionStatus.PENDING_APPROVAL);
		assertFalse(pending.isEmpty());

		saved.setStatus(DecisionStatus.APPROVED);
		saved.setResolvedAt(Instant.now());
		repository.save(saved);

		AgentDecisionLog updated = repository.findById(saved.getId()).orElseThrow();
		assertEquals(DecisionStatus.APPROVED, updated.getStatus());
		assertNotNull(updated.getResolvedAt());
	}

	@Test
	void testKafkaAlertConsumer() {
		int initialCount = agentDecisionService.getReceivedEvents().size();

		AlertingEvent event = AlertingEvent.builder()
				.userId(999L)
				.message("Energy consumption threshold exceeded")
				.threshold(500.0)
				.energyConsumed(750.0)
				.email("test-user@example.com")
				.build();

		testKafkaTemplate.send("energy-alerts", event);

		await().atMost(Duration.ofSeconds(10)).until(() ->
				agentDecisionService.getReceivedEvents().size() > initialCount
		);

		AlertingEvent received = agentDecisionService.getReceivedEvents().stream()
				.filter(e -> Long.valueOf(999L).equals(e.getUserId()))
				.findFirst()
				.orElseThrow();

		assertEquals("Energy consumption threshold exceeded", received.getMessage());
		assertEquals(500.0, received.getThreshold());
		assertEquals(750.0, received.getEnergyConsumed());
	}
}
