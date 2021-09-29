package io.sapl.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.sapl.spring.pep.PolicyEnforcementPoint;

@Configuration
@Import(PolicyEnforcementPoint.class)
public class PolicyEnforcementPointAutoconfiguration {

}