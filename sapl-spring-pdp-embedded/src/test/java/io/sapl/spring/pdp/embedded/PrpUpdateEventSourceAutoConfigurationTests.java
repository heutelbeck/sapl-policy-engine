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

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;

class PrpUpdateEventSourceAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(SAPLInterpreter.class, () -> mock(SAPLInterpreter.class))
			.withConfiguration(AutoConfigurations.of(PrpUpdateEventSourceAutoConfiguration.class));

	@TempDir
	File tempDir;

	@Test
	void whenFilesystemPrpIsConfigured_thenOneIsCreated() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM", "io.sapl.pdp.embedded.index=NAIVE",
						"io.sapl.pdp.embedded.configPath=" + tempDir, "io.sapl.pdp.embedded.policiesPath=" + tempDir)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PrpUpdateEventSource.class);
					assertThat(context).hasSingleBean(FileSystemPrpUpdateEventSource.class);
				});
	}

	@Test
	void whenReourcesPrpIsConfigured_thenOneIsCreated() {
		contextRunner
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOURCES", "io.sapl.pdp.embedded.index=NAIVE",
						"io.sapl.pdp.embedded.configPath=/", "io.sapl.pdp.embedded.policiesPath=/")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PrpUpdateEventSource.class);
					assertThat(context).hasSingleBean(ResourcesPrpUpdateEventSource.class);
				});
	}

	@Test
	void whenPrpPresent_thenDoNotLoadANewOne() {
		contextRunner.withBean(PrpUpdateEventSource.class, () -> mock(PrpUpdateEventSource.class))
				.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOURCES", "io.sapl.pdp.embedded.index=NAIVE")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PrpUpdateEventSource.class);
					assertThat(context).doesNotHaveBean(ResourcesPrpUpdateEventSource.class);
					assertThat(context).doesNotHaveBean(FileSystemPrpUpdateEventSource.class);
				});
	}

}
