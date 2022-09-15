/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.providers.ContentFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ContentFilteringProvider;

@Configuration
@Import(value = { ConstraintEnforcementService.class })
public class ConstraintsHandlerAutoconfiguration {
	@Bean
	ContentFilteringProvider jsonNodeContentFilteringProvider(ObjectMapper objectMapper) {
		return new ContentFilteringProvider(objectMapper);
	}

	@Bean
	ContentFilterPredicateProvider contentFilterPredicateProvider(ObjectMapper objectMapper) {
		return new ContentFilterPredicateProvider(objectMapper);
	}
}