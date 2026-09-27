package com.lightwise.device_service.service;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.lightwise.device_service.entity.Device;
import com.lightwise.device_service.entity.DeviceCommandLog;
import com.lightwise.device_service.dto.CommandRequest;
import com.lightwise.device_service.dto.DeviceDto;
import com.lightwise.device_service.repository.DeviceCommandLogRepository;
import com.lightwise.device_service.repository.DeviceRepository;
import com.lightwise.device_service.exception.DeviceNotFoundException;
import com.lightwise.device_service.model.CommandStatus;
import com.lightwise.device_service.model.DeviceAction;

@Service
public class DeviceService {

  private final DeviceRepository deviceRepository;
  private final DeviceCommandLogRepository commandLogRepository;

  public DeviceService(DeviceRepository deviceRepository, DeviceCommandLogRepository commandLogRepository) {
    this.deviceRepository = deviceRepository;
    this.commandLogRepository = commandLogRepository;
  }

  public DeviceDto getDeviceById(Long id) {
    Device device = deviceRepository.findById(id)
        .orElseThrow(() -> new DeviceNotFoundException("Device not found with id " + id));
    return mapToDto(device);
  }

  public List<DeviceDto> getAllDevicesByUserId(Long userId) {
    List<Device> devices = deviceRepository.findAllByUserId(userId);
    return devices.stream()
        .map(this::mapToDto)
        .toList();
  }

  private DeviceDto mapToDto(Device device) {
    DeviceDto dto = new DeviceDto();
    dto.setId(device.getId());
    dto.setName(device.getName());
    dto.setType(device.getType());
    dto.setLocation(device.getLocation());
    dto.setUserId(device.getUserId());
    return dto;
  }

  public DeviceDto createDevice(DeviceDto input) {
    Device device = new Device();
    device.setName(input.getName());
    device.setType(input.getType());
    device.setLocation(input.getLocation());
    device.setUserId(input.getUserId());
    final Device savedDevice = deviceRepository.save(device);
    return mapToDto(savedDevice);
  }

  public DeviceDto updateDevice(Long id, DeviceDto input) {
    Device device = deviceRepository.findById(id)
        .orElseThrow(() -> new DeviceNotFoundException("Device not found with id " + id));
    device.setName(input.getName());
    device.setType(input.getType());
    device.setLocation(input.getLocation());
    device.setUserId(input.getUserId());
    final Device savedDevice = deviceRepository.save(device);
    return mapToDto(savedDevice);
  }

  public void deleteDevice(Long id) {
    if (!deviceRepository.existsById(id)) {
      throw new DeviceNotFoundException("Device not found with id " + id);
    }
    deviceRepository.deleteById(id);
  }

  public ResponseEntity<?> sendCommand(Long id, CommandRequest request) {
    Device device = deviceRepository.findById(id)
        .orElseThrow(() -> new DeviceNotFoundException("Device not found for id: " + id));

    boolean neverShutOff = Boolean.TRUE.equals(device.getNeverShutOff());

    if (neverShutOff && request.action() == DeviceAction.SHUTOFF) {
      commandLogRepository.save(new DeviceCommandLog(id, request.action(), CommandStatus.BLOCKED));
      return ResponseEntity.status(409).body("blocked: device flagged never_shut_off");
    }

    // mock adapter call for now
    // deviceAdapter.send(device, request.action());

    boolean newIsOn = request.action() == DeviceAction.RESUME;
    device.setIsOn(newIsOn);
    deviceRepository.save(device);

    commandLogRepository.save(new DeviceCommandLog(id, request.action(), CommandStatus.EXECUTED));
    return ResponseEntity.ok().build();
  }
}
