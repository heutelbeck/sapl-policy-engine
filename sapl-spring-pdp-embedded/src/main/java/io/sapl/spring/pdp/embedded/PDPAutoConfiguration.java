/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
@AutoConfigureAfter({ FunctionLibrariesAutoConfiguration.class, PolicyInformationPointsAutoConfiguration.class })
public class PDPAutoConfiguration {

	private final PolicyRetrievalPoint prp;
	private final PDPConfigurationProvider pdpConfigProvider;
	private final FunctionContext functionCtx;

	private final Collection<Object> policyInformationPoints;

	public PDPAutoConfiguration(ConfigurableApplicationContext applicationContext, PolicyRetrievalPoint prp,
			PDPConfigurationProvider pdpConfigProvider, FunctionContext functionCtx) {
		this.prp = prp;
		this.functionCtx = functionCtx;
		this.pdpConfigProvider = pdpConfigProvider;
		policyInformationPoints = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class).values();
	}

	@Bean
	@ConditionalOnMissingBean
	public PolicyDecisionPoint policyDecisionPoint()
			throws FunctionException, AttributeException, IOException, URISyntaxException, PolicyEvaluationException {
		var builder = EmbeddedPolicyDecisionPoint.builder().withFunctionContext(functionCtx)
				.withPDPConfigurationProvider(pdpConfigProvider).withPolicyRetrievalPoint(prp);
		return bindComponentsToPDP(builder).build();
	}

	private Builder bindComponentsToPDP(Builder builder) throws AttributeException {
		for (var entry : policyInformationPoints) {
			log.debug("binding PIP to PDP: {}", entry.getClass().getSimpleName());
			try {
				builder.withPolicyInformationPoint(entry);
			} catch (SecurityException | IllegalArgumentException | AttributeException e) {
				throw new AttributeException(e);
			}
		}
		return builder;
	}

}
