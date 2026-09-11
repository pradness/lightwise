package com.lightwise.device_service.service;

import org.springframework.stereotype.Service;

import com.lightwise.device_service.entity.Device;
import com.lightwise.device_service.dto.DeviceDto;
import com.lightwise.device_service.repository.DeviceRepository;
import com.lightwise.device_service.exception.DeviceNotFoundException;


@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public DeviceDto getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                    .orElseThrow(() -> new DeviceNotFoundException("Device not found with id " + id));
        return mapToDto(device);
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
}