CREATE TABLE IF NOT EXISTS `agent_decision_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_id` BIGINT NOT NULL,
    `proposed_action` VARCHAR(20) NOT NULL,
    `reasoning` TEXT,
    `status` VARCHAR(30) NOT NULL,
    `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `resolved_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_agent_decision_log_device_id` (`device_id`),
    KEY `idx_agent_decision_log_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
