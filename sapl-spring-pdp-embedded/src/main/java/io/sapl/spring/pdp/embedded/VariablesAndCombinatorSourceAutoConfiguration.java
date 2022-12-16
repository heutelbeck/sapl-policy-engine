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
package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class VariablesAndCombinatorSourceAutoConfiguration {

	private final EmbeddedPDPProperties pdpProperties;

	@Bean
	@ConditionalOnMissingBean(VariablesAndCombinatorSource.class)
	VariablesAndCombinatorSource variablesAndCombinatorSource() throws InitializationException {
		log.info("Deploying {} VariablesAndCombinatorSource configuration provider. Sourcing data from: {}",
				pdpProperties.getPdpConfigType(), pdpProperties.getConfigPath());

		if (pdpProperties.getPdpConfigType() == PDPDataSource.FILESYSTEM)
			return new FileSystemVariablesAndCombinatorSource(pdpProperties.getConfigPath());

		return new ResourcesVariablesAndCombinatorSource(pdpProperties.getConfigPath());
	}

}
