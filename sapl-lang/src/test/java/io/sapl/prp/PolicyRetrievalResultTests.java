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
package io.sapl.prp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.DefaultSAPLInterpreter;

class PolicyRetrievalResultTests {
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void whenNoArgsConstructorUsedThenHasExpectedState() {
        var sut = new PolicyRetrievalResult();
        assertThat(sut.getMatchingDocuments(), is(empty()));
        assertThat(sut.isPrpValidState(), is(true));
        assertThat(sut.isErrorsInTarget(), is(false));
    }

    @Test
    void whenAllArgsConstructorUsedThenHasProvidedState() {
        var docs = new ArrayList<MatchingDocument>();
        var doc  = INTERPRETER.parseDocument("policy \"x\" permit");
        docs.add(new MatchingDocument(doc, Val.TRUE));
        var sut = new PolicyRetrievalResult(docs, true, false);
        assertThat(sut.getMatchingDocuments().get(0).document(), is(doc));
        assertThat(sut.isPrpValidState(), is(false));
        assertThat(sut.isErrorsInTarget(), is(true));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithMatchAddsDocument() {
        var sut = new PolicyRetrievalResult();
        var doc = INTERPRETER.parseDocument("policy \"x\" permit");
        sut = sut.withMatch(doc, Val.TRUE);
        assertThat(sut.getMatchingDocuments().get(0).document(), is(doc));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithErrorTurnsOnErrorState() {
        var sut = new PolicyRetrievalResult();
        sut = sut.withError();
        assertThat(sut.isErrorsInTarget(), is(true));
    }

    @Test
    void whenNoArgsConstructorUsedThenWithInvalidStateTurnsOnInvalidState() {
        var sut = new PolicyRetrievalResult();
        sut = sut.withInvalidState();
        assertThat(sut.isPrpValidState(), is(false));
    }

}
