package io.sapl.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.sapl.spring.constraints.ConstraintEnforcementService;

@Configuration
@Import(value = { ConstraintEnforcementService.class })
public class ConstraintsHandlerAutoconfiguration {

}