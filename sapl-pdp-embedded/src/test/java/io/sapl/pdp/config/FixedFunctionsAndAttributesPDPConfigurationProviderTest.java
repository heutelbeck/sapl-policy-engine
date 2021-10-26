/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;

class FixedFunctionsAndAttributesPDPConfigurationProviderTest {

    @Test
    void do_test() {
        var source = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
        var attrCtx = new AnnotationAttributeContext();
        var funcCtx = new AnnotationFunctionContext();
        var provider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attrCtx, funcCtx, source);
        var config = provider.pdpConfiguration().blockFirst();
        provider.dispose();


        assertThat(config.getDocumentsCombinator() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
        assertThat(config.getPdpScopedEvaluationContext().getAttributeCtx(), is(attrCtx));
        assertThat(config.getPdpScopedEvaluationContext().getFunctionCtx(), is(funcCtx));
        assertThat(config.isValid(), is(true));
    }
}
