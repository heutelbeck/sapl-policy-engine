/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;

class AllAutoConfigurationsIntegrationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(PrpUpdateEventSourceAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(AttributeContextAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(DocumentationAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(FunctionContextAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(FunctionLibrariesAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(InterpreterAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(PDPConfigurationProviderAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(PolicyInformationPointsAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(VariablesAndCombinatorSourceAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(PrpUpdateEventSourceAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(PRPAutoConfiguration.class))
			.withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class));

	@TempDir
	File tempDir;

	@Test
	void whenFilesystemPrpIsConfiguredAndTheEntireAutoconfigurationRuns_thenAnEmbeddedPDPIsCreated() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM", "io.sapl.pdp.embedded.index=NAIVE",
						"io.sapl.pdp.embedded.configPath=" + tempDir, "io.sapl.pdp.embedded.policiesPath=" + tempDir)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
					assertThat(context).hasSingleBean(EmbeddedPolicyDecisionPoint.class);
				});
	}

	@Test
	void whenResourcesPrpIsConfiguredAndTheEntireAutoconfigurationRuns_thenAnEmbeddedPDPIsCreated() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOURCES", "io.sapl.pdp.embedded.index=NAIVE",
						"io.sapl.pdp.embedded.configPath=/", "io.sapl.pdp.embedded.policiesPath=/")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
					assertThat(context).hasSingleBean(EmbeddedPolicyDecisionPoint.class);
				});
	}

}
