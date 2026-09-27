package com.lightwise.device_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import com.lightwise.device_service.model.CommandStatus;
import com.lightwise.device_service.model.DeviceAction;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "device_command_log")
public class DeviceCommandLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeviceAction action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CommandStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public DeviceCommandLog(Long deviceId, DeviceAction action, CommandStatus status) {
    this.deviceId = deviceId;
    this.action = action;
    this.status = status;
    this.createdAt = Instant.now();
  }

}
