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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import io.sapl.pdp.embedded.config.filesystem.FilesystemPDPConfigurationProvider;
import io.sapl.pdp.embedded.config.resources.ResourcesPDPConfigurationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PDPConfigurationProviderAutoConfiguration {

	private final EmbeddedPDPProperties pdpProperties;

	@Bean
	@ConditionalOnMissingBean
	public PDPConfigurationProvider pdpConfigurationProvider()
			throws PDPConfigurationException, IOException, URISyntaxException {
		var configPath = pdpProperties.getConfigPath();
		if (pdpProperties.getPdpConfigType() == EmbeddedPDPProperties.PDPDataSource.FILESYSTEM) {
			log.info("using monitored config file from the filesystem: {}", configPath);
			return new FilesystemPDPConfigurationProvider(configPath);
		} else {
			log.info("using static config file from bundled resource at: {}", configPath);
			return new ResourcesPDPConfigurationProvider(ResourcesPDPConfigurationProvider.class, configPath);
		}
	}
}
