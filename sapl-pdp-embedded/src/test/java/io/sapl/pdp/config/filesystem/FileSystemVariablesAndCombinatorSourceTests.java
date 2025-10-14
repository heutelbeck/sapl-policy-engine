/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class FileSystemVariablesAndCombinatorSourceTest {

    @Test
    void loadExistingConfigTest() {
        final var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/valid_config");
        final var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        final var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() == PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY, is(Boolean.TRUE));
        assertThat(variables.get().size(), is(2));
    }

    @Test
    void return_default_config_for_missing_configuration_file() {
        final var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources");
        final var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        final var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() == PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, is(Boolean.TRUE));
        assertThat(variables.get().size(), is(0));
    }

    @Test
    void return_empty_optional_for_exception_during_config_load() {
        final var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/broken_config");
        final var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        final var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();
        assertThat(algo.isEmpty(), is(true));
        assertThat(variables.isEmpty(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "src/test/resources/valid_config", "src/test/resources/valid_config2",
            "src/test/resources/valid_config3", "src/test/resources/valid_config4" })
    void test_process_watcher_event(String path) {
        try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
            mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
                    .thenReturn(Flux.just(new FileCreatedEvent(null), new FileDeletedEvent(null)));

            mock.when(() -> FileMonitorUtil.resolveHomeFolderIfPresent(any())).thenCallRealMethod();

            final var configProvider = new FileSystemVariablesAndCombinatorSource(path);
            final var algo           = configProvider.getCombiningAlgorithm().blockLast();
            configProvider.getVariables().blockFirst();
            configProvider.destroy();

            mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
            assertThat(algo.get() == PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, is(Boolean.TRUE));
        }
    }

}
