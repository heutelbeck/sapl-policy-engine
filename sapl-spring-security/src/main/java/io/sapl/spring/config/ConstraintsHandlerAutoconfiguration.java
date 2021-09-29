package io.sapl.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;

@Configuration
@Import(ReactiveConstraintEnforcementService.class)
public class ConstraintsHandlerAutoconfiguration {

}