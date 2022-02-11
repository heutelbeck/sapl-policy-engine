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
package io.sapl.pdp.config.filesystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.grammar.sapl.PermitUnlessDenyCombiningAlgorithm;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import reactor.core.publisher.Flux;

class FileSystemVariablesAndCombinatorSourceTest {

	@Test
	void loadExistingConfigTest() {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/valid_config");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(2));
	}

	@Test
	void return_default_config_for_missing_configuration_file() {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(0));
	}

	@Test
	void return_empty_optional_for_exception_during_config_load() throws Exception {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/broken_config");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();
		assertThat(algo.isEmpty(), is(true));
		assertThat(variables.isEmpty(), is(true));
	}

	@Test
	void test_process_watcher_event() {
		try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
			mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
					.thenReturn(Flux.just(new FileCreatedEvent(null), new FileDeletedEvent(null)));

			mock.when(() -> FileMonitorUtil.resolveHomeFolderIfPresent(any())).thenCallRealMethod();

			var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/valid_config");
			var algo = configProvider.getCombiningAlgorithm().blockLast();
			configProvider.getVariables().blockFirst();
			configProvider.dispose();

			mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
			assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		}
	}

}
