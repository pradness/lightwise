package com.lightwise.api_gateway.route;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;

import java.net.URI;

import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
/**
 * UserServiceRoutes
 */
public class UserServiceRoutes {

  @Bean
  public RouterFunction<ServerResponse> userRoute() {
    return GatewayRouterFunctions.route("user-service")
        .route(RequestPredicates.path("/api/v1/users/**"), HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri("http://localhost:8080"))
        .filter(CircuitBreakerFilterFunctions.circuitBreaker(
            "userServiceCircuitBreaker",
            URI.create("forward:/fallbackRoute")))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> userFallbackRoute() {
    return GatewayRouterFunctions.route("fallbackRoute")
        .route(RequestPredicates.path("/fallbackRoute"),
            request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("User service is down"))
        .build();
  }
}
