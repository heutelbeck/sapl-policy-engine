package io.sapl.prp.index.naive;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NaiveImmutableParsedDocumentIndexTest {

    @Test
    void testConstruction() {
        val index = new NaiveImmutableParsedDocumentIndex();
        assertThat(index, notNullValue());

        assertThat(index.toString(), is("NaiveImmutableParsedDocumentIndex(documents={}, consistent=true)"));
    }

    @Test
    void should_return_empty_result_when_no_documents_are_published() {
        val index = new NaiveImmutableParsedDocumentIndex();
        var result = index.retrievePolicies(null).block();

        assertThat(result, notNullValue());
        assertThat(result.isErrorsInTarget(), is(false));
        assertThat(result.isPrpValidState(), is(true));
        assertThat(result.getMatchingDocuments().isEmpty(), is(true));


        val saplMock1 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock1.getPolicyElement().getSaplName()).thenReturn("SAPL1");
        when(saplMock1.matches(any())).thenReturn(Mono.just(Val.TRUE));

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null, "null"));
        updates.add(new Update(Type.PUBLISH, saplMock1, "SAPL1"));
        updates.add(new Update(Type.UNPUBLISH, saplMock1, "SAPL1"));
        var event = new PrpUpdateEvent(updates);

        val index2 = index.apply(event);
        result = index2.retrievePolicies(null).block();

        assertThat(result.getMatchingDocuments().isEmpty(), is(true));

    }


    @Test
    void should_return_invalid_result_when_inconsistent_event_was_published() {
        val index = new NaiveImmutableParsedDocumentIndex();
        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.INCONSISTENT, null, "null"));
        var event = new PrpUpdateEvent(updates);
        val index2 = index.apply(event);

        val result = index2.retrievePolicies(null).block();

        assertThat(result.isPrpValidState(), is(false));
    }

    @Test
    void testApply() {
        val index = new NaiveImmutableParsedDocumentIndex();

        val saplMock1 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock1.getPolicyElement().getSaplName()).thenReturn("SAPL1");
        when(saplMock1.matches(any())).thenReturn(Mono.just(Val.TRUE));

        val saplMock2 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock2.getPolicyElement().getSaplName()).thenReturn("SAPL2");
        when(saplMock2.matches(any())).thenReturn(Mono.just(Val.error()));

        val saplMock3 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        val valMock = mock(Val.class);
        when(valMock.isBoolean()).thenReturn(false);
        when(valMock.getMessage()).thenReturn("i'm not a boolean");
        when(saplMock3.getPolicyElement().getSaplName()).thenReturn("SAPL3");
        when(saplMock3.matches(any())).thenReturn(Mono.just(valMock));


        val saplMock4 = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock4.getPolicyElement().getSaplName()).thenReturn("SAPL4");
        when(saplMock4.matches(any())).thenReturn(Mono.just(Val.FALSE));

        List<Update> updates = new ArrayList<>();
        updates.add(new Update(Type.CONSISTENT, null, "null"));
        updates.add(new Update(Type.PUBLISH, saplMock1, "SAPL1"));
        updates.add(new Update(Type.PUBLISH, saplMock2, "SAPL2"));
        updates.add(new Update(Type.PUBLISH, saplMock3, "SAPL3"));
        updates.add(new Update(Type.PUBLISH, saplMock4, "SAPL4"));
        var event = new PrpUpdateEvent(updates);

        val index2 = index.apply(event);
        assertThat(index.equals(index2), is(false));

        val result = index2.retrievePolicies(null).block();
        assertThat(result.getMatchingDocuments().isEmpty(), is(false));
    }
}
