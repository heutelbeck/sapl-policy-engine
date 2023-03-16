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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbeddedPDPPropertiesValidationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(EnablePropertiesInApplicationTestRunnerConfiguration.class);

	@TempDir
	File tempDir;

	@EnableConfigurationProperties(EmbeddedPDPProperties.class)
	static class EnablePropertiesInApplicationTestRunnerConfiguration {

	}

	@Test
	void whenValidProertiesArePresent_thenPropertiesLoad() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILEsystem",
						"io.sapl.pdp.embedded.index=CaNoNiCaL", "io.sapl.pdp.embedded.configPath=" + tempDir,
						"io.sapl.pdp.embedded.policiesPath=" + tempDir)
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void whenValidProertiesWithMissingPathsArePresent_thenPropertiesFallBackToDefaults() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM", "io.sapl.pdp.embedded.index=NAIVE")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(EmbeddedPDPProperties.class).getConfigPath()).isEqualTo("/policies");
					assertThat(context.getBean(EmbeddedPDPProperties.class).getPoliciesPath()).isEqualTo("/policies");
				});
	}

	@Test
	void whenPathsAreSetToNull_thenContextFailsLoading() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM", "io.sapl.pdp.embedded.index=NAIVE",
						"io.sapl.pdp.embedded.configPath=", "io.sapl.pdp.embedded.policiesPath=")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void whenPdpConfigTypeIsInvalid_thenContextFailsLoading() {
		contextRunner.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=I AM INVALID",
				"io.sapl.pdp.embedded.index=NAIVE", "io.sapl.pdp.embedded.configPath=" + tempDir,
				"io.sapl.pdp.embedded.policiesPath=" + tempDir).run(context -> assertThat(context).hasFailed());
	}

	@Test
	void whenIndexIsInvalid_thenContextFailsLoading() {
		contextRunner.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOUORCES",
				"io.sapl.pdp.embedded.index=I AM INVALID", "io.sapl.pdp.embedded.configPath=" + tempDir,
				"io.sapl.pdp.embedded.policiesPath=" + tempDir).run(context -> assertThat(context).hasFailed());
	}

}
