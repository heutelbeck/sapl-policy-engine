package io.sapl.spring.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({ "io.sapl.spring.constraints", "io.sapl.spring.pep" })
public class PolicyEnforcementPointAutoConfiguration {

}
