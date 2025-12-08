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
package io.sapl.grammar.ui.contentassist;

import io.sapl.pdp.config.PDPConfigurationProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SAPLUiContentProposalProviderTest {

    @Test
    void getPDPConfigurationProvider() {
        PDPConfigurationProvider pdpConfigurationProvider = () -> null;
        final var                sut                      = new SAPLUiContentProposalProvider(pdpConfigurationProvider);
        assertThat(sut.getContentAssistConfigurationSource(), is(pdpConfigurationProvider));
    }
}
