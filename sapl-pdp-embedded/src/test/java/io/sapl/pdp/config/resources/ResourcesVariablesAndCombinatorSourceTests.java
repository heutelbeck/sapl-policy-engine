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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.DenyOverridesCombiningAlgorithm;
import io.sapl.grammar.sapl.PermitUnlessDenyCombiningAlgorithm;
import io.sapl.interpreter.InitializationException;
import io.sapl.util.JarCreator;
import io.sapl.util.JarUtil;

class ResourcesVariablesAndCombinatorSourceTests {

    @Test
    void test_guard_clauses() {
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource("", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesVariablesAndCombinatorSource(null, mock(ObjectMapper.class)));

        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, "", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesVariablesAndCombinatorSource(null, null, mock(ObjectMapper.class)));
        var thisClass = this.getClass();
        assertThrows(NullPointerException.class,
                () -> new ResourcesVariablesAndCombinatorSource(thisClass, null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(thisClass, "", null));

    }

    @Test
    void ifExecutedDuringUnitTests_thenLoadConfigurationFileFromFileSystem() throws Exception {
        var configProvider = new ResourcesVariablesAndCombinatorSource("/valid_config");
        var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithm, is(true));
        assertThat(variables.get().size(), is(2));
    }

    @Test
    void ifExecutedDuringUnitTestsAndNoConfigFilePresent_thenLoadDefaultConfiguration() throws Exception {
        var configProvider = new ResourcesVariablesAndCombinatorSource("");
        var algo           = configProvider.getCombiningAlgorithm().blockFirst();
        var variables      = configProvider.getVariables().blockFirst();
        configProvider.destroy();

        assertThat(algo.get() instanceof DenyOverridesCombiningAlgorithm, is(true));
        assertThat(variables.get().size(), is(0));
    }

    @Test
    void ifExecutedDuringUnitTestsAndConfigFileBroken_thenPropagateException() {
        assertThrows(InitializationException.class, () -> new ResourcesVariablesAndCombinatorSource("/broken_config"));
    }

    @Test
    void ifExecutedInJar_thenLoadConfigurationFileFromJar(@TempDir(cleanup = CleanupMode.ALWAYS) Path tempDir)
            throws Exception {
        var url = JarCreator.createPoliciesInJar("!/policies", tempDir);
        try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
            mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);

            var configProvider = new ResourcesVariablesAndCombinatorSource();
            var algo           = configProvider.getCombiningAlgorithm().blockFirst();
            var variables      = configProvider.getVariables().blockFirst();
            configProvider.destroy();

            assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithm, is(true));
            assertThat(variables.get().size(), is(2));
        }
    }

    @Test
    void ifExecutedInJarAndConfigFileBroken_thenPropagateException(@TempDir(cleanup = CleanupMode.ALWAYS) Path tempDir)
            throws IOException, URISyntaxException {
        var url = JarCreator.createBrokenPoliciesInJar("!/policies", tempDir);
        try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
            mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);
            assertThrows(InitializationException.class, () -> new ResourcesVariablesAndCombinatorSource("/policies"));
        }
    }

    @Test
    void ifExecutedInJarAndNoConfigFilePresent_thenLoadDefaultConfiguration(
            @TempDir(cleanup = CleanupMode.ALWAYS) Path tempDir) throws Exception {
        var url = JarCreator.createPoliciesInJar("!/not_existing", tempDir);
        try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
            mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);

            var configProvider = new ResourcesVariablesAndCombinatorSource("/not_existing");
            var algo           = configProvider.getCombiningAlgorithm().blockFirst();
            var variables      = configProvider.getVariables().blockFirst();
            configProvider.destroy();

            assertThat(algo.get() instanceof DenyOverridesCombiningAlgorithm, is(true));
            assertThat(variables.get().size(), is(0));
        }
    }

}
