/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.filter.PolicyEnforcementFilterPEP;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class FilterPEPAutoConfiguration {
	@Bean
	@ConditionalOnProperty("io.sapl.policyEnforcementFilter")
	public PolicyEnforcementFilterPEP policyEnforcementFilter(PolicyDecisionPoint pdp,
			ConstraintEnforcementService constraintHandlers, ObjectMapper mapper) {
		log.info("PolicyEnforcementFilter enabled.");
		return new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
	}
}
