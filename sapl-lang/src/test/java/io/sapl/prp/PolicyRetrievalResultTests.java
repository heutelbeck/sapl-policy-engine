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
package io.sapl.prp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.DefaultSAPLInterpreter;

class PolicyRetrievalResultTests {
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void whenNoArgsConstructorUsedThenHasExpectedState() {
        final var sut = new PolicyRetrievalResult();
        assertThat(sut.getMatchingDocuments(), is(empty()));
        assertThat(sut.isPrpInconsistent(), is(false));
        assertThat(sut.isRetrievalWithErrors(), is(false));
    }

    @Test
    void whenAllArgsConstructorUsedThenHasProvidedState() {
        final var docs = new ArrayList<DocumentMatch>();
        final var doc  = INTERPRETER.parseDocument("policy \"x\" permit");
        docs.add(new DocumentMatch(doc, Val.TRUE));
        final var sut = new PolicyRetrievalResult(docs, true);
        assertThat(sut.getMatchingDocuments().get(0).document(), is(doc));
        assertThat(sut.isPrpInconsistent(), is(false));
        assertThat(sut.isRetrievalWithErrors(), is(true));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithMatchAddsDocument() {
        final var doc = INTERPRETER.parseDocument("policy \"x\" permit");
        var       sut = new PolicyRetrievalResult();
        sut = sut.withMatch(new DocumentMatch(doc, Val.TRUE));
        assertThat(sut.getMatchingDocuments().get(0).document(), is(doc));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithMatchAddsDocumentWithError() {
        final var doc = INTERPRETER.parseDocument("policy \"x\" permit");
        var       sut = new PolicyRetrievalResult();
        sut = sut.withMatch(new DocumentMatch(doc, ErrorFactory.error("")));
        assertThat(sut.getNonMatchingDocuments().get(0).document(), is(doc));
        assertThat(sut.getMatchingDocuments(), empty());
        assertThat(sut.isRetrievalWithErrors(), is(true));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithMatchAddsDocumentWithFalse() {
        final var doc = INTERPRETER.parseDocument("policy \"x\" permit");
        var       sut = new PolicyRetrievalResult();
        sut = sut.withMatch(new DocumentMatch(doc, Val.FALSE));
        assertThat(sut.getNonMatchingDocuments().get(0).document(), is(doc));
        assertThat(sut.getMatchingDocuments(), empty());
        assertThat(sut.isRetrievalWithErrors(), is(false));
    }

    @Test
    void invalidPrpResult() {
        final var sut = PolicyRetrievalResult.invalidPrpResult();
        assertThat(sut.isPrpInconsistent(), is(true));
    }

    @Test
    void retrievalErrorResult() {
        final var sut = PolicyRetrievalResult.retrievalErrorResult("error");
        assertThat(sut.isPrpInconsistent(), is(false));
        assertThat(sut.isRetrievalWithErrors(), is(true));
    }
}
