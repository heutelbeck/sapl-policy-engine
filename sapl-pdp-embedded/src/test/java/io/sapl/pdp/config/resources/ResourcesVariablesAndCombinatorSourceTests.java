/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.config.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;

class ResourcesVariablesAndCombinatorSourceTests {

    @Test
    void test_guard_clauses() {
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource("", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesVariablesAndCombinatorSource(null, mock(ObjectMapper.class)));
    }

    @Test
    void ifExecutedDuringUnitTests_thenLoadConfigurationFileFromFileSystem() throws Exception {
        final var configProvider = new ResourcesVariablesAndCombinatorSource("/valid_config");
        final var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        final var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() == PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY, is(Boolean.TRUE));
        assertThat(variables.get().size(), is(2));
    }

    @Test
    void ifExecutedDuringUnitTestsAndNoConfigFilePresent_thenLoadDefaultConfiguration() throws Exception {
        final var configProvider = new ResourcesVariablesAndCombinatorSource("");
        final var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        final var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() == PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, is(Boolean.TRUE));
        assertThat(variables.get().size(), is(0));
    }

    @Test
    void ifExecutedDuringUnitTestsAndConfigFileBroken_thenPropagateException() {
        assertThrows(InitializationException.class, () -> new ResourcesVariablesAndCombinatorSource("/broken_config"));
    }

}
