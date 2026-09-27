package com.lightwise.api_gateway.route;

import java.net.URI;

import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class AgentServiceRoutes {

  @Bean
  public RouterFunction<ServerResponse> agentRoute() {
    return GatewayRouterFunctions.route("agent-service")
        .route(RequestPredicates.path("/api/v1/agent/**"), HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri("http://localhost:8086"))
        .filter(CircuitBreakerFilterFunctions.circuitBreaker(
            "agentServiceCircuitBreaker",
            URI.create("forward:/fallbackRoute")))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> agentFallbackRoute() {
    return GatewayRouterFunctions.route("agentFallbackRoute")
        .route(RequestPredicates.path("/fallbackRoute"),
            request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Agent service is down"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> agentServiceApiDocs() {
    return GatewayRouterFunctions.route("agent-service-api-docs")
        .route(RequestPredicates.path("/docs/agent-service/v3/api-docs"),
            HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri("http://localhost:8086"))
        .filter(FilterFunctions.setPath("/v3/api-docs"))
        .build();
  }
}
