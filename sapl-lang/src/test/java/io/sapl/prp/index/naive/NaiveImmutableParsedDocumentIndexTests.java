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
package io.sapl.prp.index.naive;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import reactor.core.publisher.Mono;

class NaiveImmutableParsedDocumentIndexTests {
    private static final SAPLInterpreter INTERPERETER = new DefaultSAPLInterpreter();

    @Test
    void testConstruction() {
        final var index = new NaiveImmutableParsedDocumentIndex();
        assertThat(index, notNullValue());
    }

    @Test
    void should_return_empty_result_when_no_documents_are_published() {
        final var index  = new NaiveImmutableParsedDocumentIndex();
        var       result = index.retrievePolicies().block();

        assertThat(result, notNullValue());
        assertThat(result.isRetrievalWithErrors(), is(false));
        assertThat(result.isPrpInconsistent(), is(false));
        assertThat(result.getMatchingDocuments().isEmpty(), is(true));

        final var document = INTERPERETER.parseDocument("policy \"p_0\" permit !resource.x1");

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null));
        updates.add(new Update(Type.PUBLISH, document));
        updates.add(new Update(Type.WITHDRAW, document));
        final var event = new PrpUpdateEvent(updates);

        final var index2 = index.apply(event);
        result = index2.retrievePolicies().block();

        assertThat(result.getMatchingDocuments().isEmpty(), is(true));

    }

    @Test
    void should_return_invalid_result_when_inconsistent_event_was_published() {
        final var    index   = new NaiveImmutableParsedDocumentIndex();
        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.INCONSISTENT, null));
        final var event  = new PrpUpdateEvent(updates);
        final var index2 = index.apply(event);

        final var result = index2.retrievePolicies().block();

        assertThat(result.isPrpInconsistent(), is(true));
    }

    @Test
    void testApply() {
        final var index = new NaiveImmutableParsedDocumentIndex();

        final var saplMock1 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock1.getPolicyElement().getSaplName()).thenReturn("SAPL1");
        when(saplMock1.matches()).thenReturn(Mono.just(Val.TRUE));

        final var saplMock2 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        final var valMock2  = mock(Val.class);
        when(valMock2.isBoolean()).thenReturn(Boolean.FALSE);
        when(valMock2.isError()).thenReturn(Boolean.TRUE);
        when(valMock2.getMessage()).thenReturn("Error Val");
        when(saplMock2.getPolicyElement().getSaplName()).thenReturn("SAPL2");
        when(saplMock2.matches()).thenReturn(Mono.just(valMock2));

        final var saplMock3 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        final var valMock3  = mock(Val.class);
        when(valMock3.isBoolean()).thenReturn(Boolean.FALSE);
        when(valMock3.getMessage()).thenReturn("i'm not a boolean");
        when(saplMock3.getPolicyElement().getSaplName()).thenReturn("SAPL3");
        when(saplMock3.matches()).thenReturn(Mono.just(valMock3));

        final var saplMock4 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock4.getPolicyElement().getSaplName()).thenReturn("SAPL4");
        when(saplMock4.matches()).thenReturn(Mono.just(Val.FALSE));

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null));
        updates.add(new Update(Type.PUBLISH, new Document("id1", "SAPL1", saplMock1, null, null)));
        updates.add(new Update(Type.PUBLISH, new Document("id2", "SAPL2", saplMock2, null, null)));
        updates.add(new Update(Type.PUBLISH, new Document("id3", "SAPL3", saplMock3, null, null)));
        updates.add(new Update(Type.PUBLISH, new Document("id4", "SAPL4", saplMock4, null, null)));
        final var event = new PrpUpdateEvent(updates);

        final var index2 = index.apply(event);
        assertThat(index.equals(index2), is(false));

        final var result = index2.retrievePolicies().block();
        assertThat(result.getMatchingDocuments().isEmpty(), is(false));
    }

}
