package io.sapl.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.constraints2.ConstraintEnforcementService;

@Configuration
@Import(value = { ReactiveConstraintEnforcementService.class, ConstraintEnforcementService.class })
public class ConstraintsHandlerAutoconfiguration {

}