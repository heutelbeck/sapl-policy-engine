package io.sapl.spring.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.manager.ReactiveSaplAuthorizationManager;
import io.sapl.spring.manager.SaplAuthorizationManager;
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
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SaplAuthorizationManager saplAuthorizationManager() {
		log.debug("Servlet-based environment detected. Deploy SaplAuthorizationManager.");
		return new SaplAuthorizationManager(pdp, constraintEnforcementService, mapper);
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
	ReactiveSaplAuthorizationManager reactiveSaplAuthorizationManager() {
		log.debug("Webflux environment detected. Deploy ReactiveSaplAuthorizationManager.");
		return new ReactiveSaplAuthorizationManager(pdp, constraintEnforcementService, mapper);
	}
}
