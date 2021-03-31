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

import java.util.Collection;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@AutoConfigureAfter(PolicyInformationPointsAutoConfiguration.class)
public class AttributeContextAutoConfiguration {

	private final Collection<Object> policyInformationPoints;

	public AttributeContextAutoConfiguration(ConfigurableApplicationContext applicationContext) {
		policyInformationPoints = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class).values();
	}

	@Bean
	@ConditionalOnMissingBean
	public AttributeContext attributeContext() throws InitializationException {
		var ctx = new AnnotationAttributeContext();
		for (var entry : policyInformationPoints) {
			log.info("loading Policy Information Point: {}", entry.getClass().getSimpleName());
			ctx.loadPolicyInformationPoint(entry);
		}
		return ctx;
	}
}
