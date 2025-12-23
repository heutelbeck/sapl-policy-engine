/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandardLibrariesLoaderTests {

    @Test
    void whenLoadingStandardConfiguration_thenAllLibrariesLoaded() {
        var config = StandardLibrariesLoader.loadStandardConfiguration("test");
        var bundle = config.documentationBundle();

        assertThat(bundle.functionLibraries()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(bundle.policyInformationPoints()).hasSize(3);
    }

    @Test
    void whenLoadingStandardConfiguration_thenFunctionBrokerIsAvailable() {
        var config = StandardLibrariesLoader.loadStandardConfiguration("test");

        assertThat(config.functionBroker()).isNotNull();
    }

    @Test
    void whenLoadingStandardConfiguration_thenCommonFunctionsAreDocumented() {
        var config = StandardLibrariesLoader.loadStandardConfiguration("test");

        var bundle       = config.documentationBundle();
        var libraryNames = bundle.functionLibraries().stream().map(lib -> lib.name()).toList();

        // Check common libraries are present
        assertThat(libraryNames).contains("standard", "filter", "string", "array", "object", "math", "time");
    }

    @Test
    void whenLoadingStandardConfiguration_thenPIPsAreDocumented() {
        var config = StandardLibrariesLoader.loadStandardConfiguration("test");

        var bundle   = config.documentationBundle();
        var pipNames = bundle.policyInformationPoints().stream().map(pip -> pip.name()).toList();

        // Check PIPs are present
        assertThat(pipNames).contains("time", "http", "jwt");
    }

}
