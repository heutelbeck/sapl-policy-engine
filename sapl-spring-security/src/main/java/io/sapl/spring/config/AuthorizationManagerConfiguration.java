package io.sapl.spring.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.reactive.ServerHttpRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.manager.ReactiveSaplAuthorizationManager;
import io.sapl.spring.manager.SaplAuthorizationManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
public class AuthorizationManagerConfiguration {
	private final PolicyDecisionPoint          pdp;
	private final ConstraintEnforcementService constraintEnforcementService;
	private final ObjectMapper                 mapper;

	@Bean
	@ConditionalOnClass(HttpServletRequest.class)
	SaplAuthorizationManager saplAuthorizationManager() {
		log.debug("Servlet-based environment detected. Deploy SaplAuthorizationManager.");
		return new SaplAuthorizationManager(pdp, constraintEnforcementService, mapper);
	}

	@Bean
	@ConditionalOnClass(ServerHttpRequest.class)
	ReactiveSaplAuthorizationManager reactiveSaplAuthorizationManager() {
		log.debug("Webflux environment detected. Deploy ReactiveSaplAuthorizationManager.");
		return new ReactiveSaplAuthorizationManager(pdp, constraintEnforcementService, mapper);
	}
}
