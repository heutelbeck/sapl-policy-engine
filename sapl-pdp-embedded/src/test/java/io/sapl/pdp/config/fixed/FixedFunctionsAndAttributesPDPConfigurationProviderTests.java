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
package io.sapl.pdp.config.fixed;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;

class FixedFunctionsAndAttributesPDPConfigurationProviderTests {

    @Test
    void do_test() {

        final var source    = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
        final var prpSource = constructFilesystemPolicyRetrievalPointSource("src/test/resources/policies");
        final var broker    = new CachingAttributeStreamBroker();
        final var funcCtx   = new AnnotationFunctionContext();
        final var provider  = new FixedFunctionsAndAttributesPDPConfigurationProvider(broker, funcCtx, source,
                List.of(), List.of(), prpSource);
        final var config    = provider.pdpConfiguration().blockFirst();
        provider.destroy();
        assertThat(config.documentsCombinator() == PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT,
                is(Boolean.TRUE));
        assertThat(config.attributeStreamBroker(), is(broker));
        assertThat(config.functionContext(), is(funcCtx));
        assertThat(config.variables(), notNullValue());
    }

    public static PolicyRetrievalPointSource constructFilesystemPolicyRetrievalPointSource(String policiesFolder) {
        final var seedIndex = constructDocumentIndex();
        final var source    = new FileSystemPrpUpdateEventSource(policiesFolder, new DefaultSAPLInterpreter());
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, source);
    }

    private static UpdateEventDrivenPolicyRetrievalPoint constructDocumentIndex() {
        return new NaiveImmutableParsedDocumentIndex();
    }
}
