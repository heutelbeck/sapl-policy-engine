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
package io.sapl.grammar.ui.contentassist;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PDPConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SAPLUiContentProposalProviderTest {

    @Test
    void getPDPConfigurationProvider() {
        var              sut              = new SAPLUiContentProposalProvider();
        PDPConfiguration pdpConfiguration = sut.getPDPConfigurationProvider().pdpConfiguration().blockFirst();

        assertThat(pdpConfiguration, is(not(nullValue())));
        assertThat(pdpConfiguration.isValid(), is(true));
        assertThat(pdpConfiguration.functionContext().getAvailableLibraries(), contains(FilterFunctionLibrary.NAME,
                StandardFunctionLibrary.NAME, TemporalFunctionLibrary.NAME, SchemaValidationLibrary.NAME));
        assertThat(pdpConfiguration.variables(), is(Map.of()));
        assertThat(pdpConfiguration.documentsCombinator(),
                is(CombiningAlgorithmFactory.getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES)));
        assertThat(pdpConfiguration.decisionInterceptorChain(), is(UnaryOperator.identity()));
        assertThat(pdpConfiguration.subscriptionInterceptorChain(), is(UnaryOperator.identity()));
    }
}
