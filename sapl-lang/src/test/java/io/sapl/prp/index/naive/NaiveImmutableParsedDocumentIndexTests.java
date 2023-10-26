/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import reactor.core.publisher.Mono;

class NaiveImmutableParsedDocumentIndexTests {

    @Test
    void testConstruction() {
        var index = new NaiveImmutableParsedDocumentIndex();
        assertThat(index, notNullValue());
    }

    @Test
    void should_return_empty_result_when_no_documents_are_published() {
        var index  = new NaiveImmutableParsedDocumentIndex();
        var result = index.retrievePolicies().block();

        assertThat(result, notNullValue());
        assertThat(result.isErrorsInTarget(), is(false));
        assertThat(result.isPrpValidState(), is(true));
        assertThat(result.getMatchingDocuments().isEmpty(), is(true));

        var saplMock1 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock1.getPolicyElement().getSaplName()).thenReturn("SAPL1");
        when(saplMock1.matches()).thenReturn(Mono.just(Val.TRUE));

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null, "null"));
        updates.add(new Update(Type.PUBLISH, saplMock1, "SAPL1"));
        updates.add(new Update(Type.WITHDRAW, saplMock1, "SAPL1"));
        var event = new PrpUpdateEvent(updates);

        var index2 = index.apply(event);
        result = index2.retrievePolicies().block();

        assertThat(result.getMatchingDocuments().isEmpty(), is(true));

    }

    @Test
    void should_return_invalid_result_when_inconsistent_event_was_published() {
        var          index   = new NaiveImmutableParsedDocumentIndex();
        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.INCONSISTENT, null, "null"));
        var event  = new PrpUpdateEvent(updates);
        var index2 = index.apply(event);

        var result = index2.retrievePolicies().block();

        assertThat(result.isPrpValidState(), is(false));
    }

    @Test
    void testApply() {
        var index = new NaiveImmutableParsedDocumentIndex();

        var saplMock1 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock1.getPolicyElement().getSaplName()).thenReturn("SAPL1");
        when(saplMock1.matches()).thenReturn(Mono.just(Val.TRUE));

        var saplMock2 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        var valMock2  = mock(Val.class);
        when(valMock2.isBoolean()).thenReturn(Boolean.FALSE);
        when(valMock2.isError()).thenReturn(Boolean.TRUE);
        when(valMock2.getMessage()).thenReturn("Error Val");
        when(saplMock2.getPolicyElement().getSaplName()).thenReturn("SAPL2");
        when(saplMock2.matches()).thenReturn(Mono.just(valMock2));

        var saplMock3 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        var valMock3  = mock(Val.class);
        when(valMock3.isBoolean()).thenReturn(Boolean.FALSE);
        when(valMock3.getMessage()).thenReturn("i'm not a boolean");
        when(saplMock3.getPolicyElement().getSaplName()).thenReturn("SAPL3");
        when(saplMock3.matches()).thenReturn(Mono.just(valMock3));

        var saplMock4 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock4.getPolicyElement().getSaplName()).thenReturn("SAPL4");
        when(saplMock4.matches()).thenReturn(Mono.just(Val.FALSE));

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null, "null"));
        updates.add(new Update(Type.PUBLISH, saplMock1, "SAPL1"));
        updates.add(new Update(Type.PUBLISH, saplMock2, "SAPL2"));
        updates.add(new Update(Type.PUBLISH, saplMock3, "SAPL3"));
        updates.add(new Update(Type.PUBLISH, saplMock4, "SAPL4"));
        var event = new PrpUpdateEvent(updates);

        var index2 = index.apply(event);
        assertThat(index.equals(index2), is(false));

        var result = index2.retrievePolicies().block();
        assertThat(result.getMatchingDocuments().isEmpty(), is(false));
    }

}
