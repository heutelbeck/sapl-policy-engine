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
package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@AutoConfigureAfter({ FunctionLibrariesAutoConfiguration.class, PolicyInformationPointsAutoConfiguration.class })
public class PDPAutoConfiguration {

	private final PolicyRetrievalPoint policyRetrievalPoint;
	private final PDPConfigurationProvider configurationProvider;

	@Bean
	@ConditionalOnMissingBean
	public PolicyDecisionPoint policyDecisionPoint() {
		log.info("Deploying embedded Policy Decision Point. ConfigProvider: {} PRP: {}",
				configurationProvider.getClass().getSimpleName(), policyRetrievalPoint.getClass().getSimpleName());
		return new EmbeddedPolicyDecisionPoint(configurationProvider, policyRetrievalPoint);
	}

}
