package com.lightwise.usage_service.client;

import com.lightwise.usage_service.dto.DeviceDto;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DeviceClient {

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public DeviceClient(@Value("${device.service.url}") String baseUrl) {
    this.restTemplate = new RestTemplate();
    this.baseUrl = baseUrl;
  }

  public DeviceDto getDeviceById(Long deviceId) {
    String url = UriComponentsBuilder.fromUriString(baseUrl).path("/{deviceId}").buildAndExpand(deviceId).toUriString();

    ResponseEntity<DeviceDto> responseEntity = restTemplate.getForEntity(url, DeviceDto.class);
    return responseEntity.getBody();
  }

  public List<DeviceDto> getAllDevicesForUser(Long userId) {
    String url = UriComponentsBuilder
        .fromUriString(baseUrl)
        .path("/user/{userId}")
        .buildAndExpand(userId)
        .toUriString();

    ResponseEntity<DeviceDto[]> response = restTemplate.getForEntity(url, DeviceDto[].class);
    DeviceDto[] devices = response.getBody();
    return devices == null ? List.of() : List.of(devices);
  }
}
