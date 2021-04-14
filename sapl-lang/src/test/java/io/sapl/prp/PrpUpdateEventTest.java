package io.sapl.prp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import lombok.val;

class PrpUpdateEventTest {

    @Test
    void should_return_empty_event_when_initialized_with_null() {
        List<Update> updates = null;
        var event = new PrpUpdateEvent(updates);

        assertThat(event, notNullValue());
    }

    @Test
    void toStringTest() {
        val saplMock = mock(SAPL.class, RETURNS_DEEP_STUBS);
        when(saplMock.toString()).thenReturn("SAPL");
        when(saplMock.getPolicyElement().getSaplName()).thenReturn("SAPL");

        val empty = new PrpUpdateEvent.Update(null, null, null);
        val valid = new PrpUpdateEvent.Update(Type.PUBLISH, saplMock, "SAPL");

        assertThat(empty.toString(), is("Update(type=null, documentName=NULL POLICY)"));
        assertThat(valid.toString(), is("Update(type=PUBLISH, documentName='SAPL')"));
    }
}
