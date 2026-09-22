package com.lightwise.alert_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lightwise.alert_service.entity.Alert;

/**
 * AlertRepository
 */
public interface AlertRepository extends JpaRepository<Alert, Long> {
}
