package com.lightwise.device_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lightwise.device_service.entity.DeviceCommandLog;

import java.util.List;

@Repository
public interface DeviceCommandLogRepository extends JpaRepository<DeviceCommandLog, Long> {
  List<DeviceCommandLog> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
}
