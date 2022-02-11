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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;

class PDPConfigurationProviderAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(VariablesAndCombinatorSource.class, () -> mock(VariablesAndCombinatorSource.class))
			.withBean(FunctionContext.class, () -> mock(FunctionContext.class))
			.withBean(AttributeContext.class, () -> mock(AttributeContext.class))
			.withConfiguration(AutoConfigurations.of(PDPConfigurationProviderAutoConfiguration.class));

	@Test
	void whenContextLoads_thenOneIsCreated() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(PDPConfigurationProvider.class);
			assertThat(context).hasSingleBean(FixedFunctionsAndAttributesPDPConfigurationProvider.class);
		});
	}

	@Test
	void whenAnotherProviderIsAlreadyPresent_thenDoNotLoadANewOne() {
		contextRunner.withBean(PDPConfigurationProvider.class, () -> mock(PDPConfigurationProvider.class))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PDPConfigurationProvider.class);
					assertThat(context).doesNotHaveBean(FixedFunctionsAndAttributesPDPConfigurationProvider.class);
				});
	}

}
