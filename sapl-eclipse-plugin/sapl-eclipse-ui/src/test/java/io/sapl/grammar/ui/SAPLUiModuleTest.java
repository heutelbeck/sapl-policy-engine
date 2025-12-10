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

import io.sapl.grammar.ui.contentassist.SAPLUiContentProposalProvider;
import org.eclipse.xtext.ui.editor.contentassist.UiToIdeContentProposalProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void when_bindContentAssistConfigurationSource_then_returnsNonNullSource() {
        var configSource = sut.bindContentAssistConfigurationSource();
        assertThat(configSource).isNotNull();
    }

    @Test
    void when_getConfigById_then_returnsConfigurationWithLoadedLibraries() {
        var configSource = sut.bindContentAssistConfigurationSource();
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
}
