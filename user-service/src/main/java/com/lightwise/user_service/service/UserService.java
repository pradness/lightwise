package com.lightwise.user_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lightwise.user_service.dto.UserDto;
import com.lightwise.user_service.entity.User;
import com.lightwise.user_service.exception.UserNotFoundException;
import com.lightwise.user_service.repository.UserRepository;

@Slf4j
@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public UserDto createUser(UserDto input) {
    final User createdUser = User.builder()
        .name(input.getName())
        .surname(input.getSurname())
        .email(input.getEmail())
        .address(input.getAddress())
        .alerting(input.isAlerting())
        .energyAlertingThreshold(input.getEnergyAlertingThreshold())
        .build();
    final User savedUser = userRepository.save(createdUser);
    return toDto(savedUser);
  }

  public UserDto getUserById(Long id) {
    return userRepository.findById(id).map(this::toDto).orElse(null);
  }

  @Transactional
  public void updateUser(Long id, UserDto input) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

    user.setName(input.getName());
    user.setSurname(input.getSurname());
    user.setEmail(input.getEmail());
    user.setAddress(input.getAddress());
    user.setAlerting(input.isAlerting());
    user.setEnergyAlertingThreshold(input.getEnergyAlertingThreshold());

    userRepository.save(user);
  }

  @Transactional
  public void deleteUser(Long id) {
    User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
    userRepository.delete(user);
  }

  private UserDto toDto(User user) {
    return UserDto.builder()
        .id(user.getId())
        .name(user.getName())
        .surname(user.getSurname())
        .email(user.getEmail())
        .address(user.getAddress())
        .alerting(user.isAlerting())
        .energyAlertingThreshold(user.getEnergyAlertingThreshold())
        .build();
  }
}
