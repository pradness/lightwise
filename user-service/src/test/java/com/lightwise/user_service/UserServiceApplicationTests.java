package com.lightwise.user_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

import com.lightwise.user_service.entity.User;
import com.lightwise.user_service.repository.UserRepository;


@Slf4j
@SpringBootTest
class UserServiceApplicationTests {

    public static final int NUMBER_OF_USERS = 10;
    
    @Autowired
    private UserRepository userRepository;
    
	@Test
	void contextLoads() {
	}

	@Disabled
	@Test
	void createUsers() {
		for (int i = 1; i <= NUMBER_OF_USERS; i++) {
			var user = User.builder()
					.name("User " + i)
					.surname("Surname " + i)
					.email("user" + i + "@example.com")
					.address("Address " + i)
					.alerting(i % 2 == 0)
					.energyAlertingThreshold(0.0)
					.build();
			userRepository.save(user);
		}
		log.info("User Repository has been populated.");
	}

}
