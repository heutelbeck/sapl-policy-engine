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
package io.sapl.grammar.ui;

import com.google.inject.Guice;
import io.sapl.grammar.ide.contentassist.ContentAssistConfigurationSource;
import io.sapl.grammar.ide.contentassist.DefaultContentAssistConfiguration;
import io.sapl.grammar.ui.contentassist.SAPLUiContentProposalProvider;
import org.eclipse.xtext.ui.editor.contentassist.UiToIdeContentProposalProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for the SAPL Eclipse UI module configuration.
 * These tests verify that the Guice bindings work correctly and that all
 * required dependencies (Jackson, function libraries, etc.) can be loaded.
 * This catches dependency issues that would cause "Failed to create injector"
 * errors at runtime in Eclipse.
 */
class SAPLUiModuleTest {

    SAPLUiModule sut = new SAPLUiModule(null);

    @Test
    void when_bindIContentProposalProvider_then_returnsUiToIdeAdapter() {
        assertThat(sut.bindIContentProposalProvider()).isEqualTo(UiToIdeContentProposalProvider.class);
    }

    @Test
    void when_bindIdeContentProposalProvider_then_returnsSAPLUiContentProposalProvider() {
        assertThat(sut.bindIdeContentProposalProvider()).isEqualTo(SAPLUiContentProposalProvider.class);
    }

    /**
     * This test is disabled because it requires Eclipse OSGi runtime.
     * The Guice injector pulls in Eclipse UI classes that have signing conflicts
     * when run in a regular Maven test environment.
     * The functionality is tested implicitly by the other tests and at runtime in
     * Eclipse.
     */
    // @Test
    void when_configure_then_bindsContentAssistConfigurationSource() {
        var injector     = Guice.createInjector(sut);
        var configSource = injector.getInstance(ContentAssistConfigurationSource.class);
        assertThat(configSource).isNotNull();
    }

    /**
     * This test verifies that all default function libraries can be loaded.
     * It catches missing dependencies like Jackson that would cause
     * NoClassDefFoundError at runtime in Eclipse.
     */
    @Test
    void when_createDefaultConfiguration_then_allLibrariesLoad() {
        assertThatCode(() -> DefaultContentAssistConfiguration.Factory.create())
                .as("Creating content assist configuration should not throw. "
                        + "If this fails, check that all required dependencies (Jackson, etc.) "
                        + "are available in the thirdparty bundle.")
                .doesNotThrowAnyException();
    }

    @Test
    void when_getConfigById_then_returnsConfigurationWithLoadedLibraries() {
        var configSource = DefaultContentAssistConfiguration.Factory.create();
        var config       = configSource.getConfigById("any-id");

        assertThat(config).isPresent();
        assertThat(config.get().pdpId()).isEqualTo("defaultPdp");
        assertThat(config.get().configurationId()).isEqualTo("default");
        assertThat(config.get().documentationBundle()).isNotNull();
        assertThat(config.get().documentationBundle().functionLibraries()).isNotEmpty();
        assertThat(config.get().documentationBundle().policyInformationPoints()).isNotEmpty();
        assertThat(config.get().functionBroker()).isNotNull();
        assertThat(config.get().attributeBroker()).isNotNull();
    }

    /**
     * Verifies that the function broker has libraries loaded.
     * This ensures the function libraries are properly loaded and functional.
     */
    @Test
    void when_functionBrokerLoaded_then_librariesRegistered() {
        var configSource = DefaultContentAssistConfiguration.Factory.create();
        var config       = configSource.getConfigById("test");

        assertThat(config).isPresent();
        var functionBroker = config.get().functionBroker();

        assertThat(functionBroker.getRegisteredLibraries()).as("Function libraries should be registered").isNotEmpty();
    }
}
