ALTER TABLE `device`
    ADD COLUMN `is_on` BOOLEAN NOT NULL DEFAULT TRUE AFTER `location`,
    ADD COLUMN `never_shut_off` BOOLEAN NOT NULL DEFAULT FALSE AFTER `is_on`;

CREATE TABLE `device_command_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `device_id` BIGINT NOT NULL,
    `action` VARCHAR(20) NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_command_log_device_id` (`device_id`),
    CONSTRAINT `fk_command_log_device_id`
        FOREIGN KEY (`device_id`) REFERENCES `device` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
