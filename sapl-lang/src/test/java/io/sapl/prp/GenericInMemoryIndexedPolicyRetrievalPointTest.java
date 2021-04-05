package io.sapl.prp;

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenericInMemoryIndexedPolicyRetrievalPointTest {

    PrpUpdateEventSource sourceMock;
    ImmutableParsedDocumentIndex indexMock;
    EvaluationContext contextMock;


    @BeforeEach
    void beforeEach() {
        sourceMock = mock(PrpUpdateEventSource.class);
        var eventMock = mock(PrpUpdateEvent.class);
        when(sourceMock.getUpdates()).thenReturn(Flux.just(eventMock));

        indexMock = mock(ImmutableParsedDocumentIndex.class);
        when(indexMock.apply(any())).thenReturn(indexMock);

        contextMock = mock(EvaluationContext.class);

    }

    @Test
    void testConstructAndRetrieveWithEmptyResult() {
        //WHEN
        var resultMock = mock(PolicyRetrievalResult.class);
        when(indexMock.retrievePolicies(any())).thenReturn(Mono.just(resultMock));

        //DO
        val prp = new GenericInMemoryIndexedPolicyRetrievalPoint(indexMock, sourceMock);
        val result = prp.retrievePolicies(contextMock).blockFirst();
        prp.dispose();

        //THEN
        verify(sourceMock, times(1)).getUpdates();
        verify(indexMock, times(1)).apply(any());
        assertThat(prp, is(notNullValue()));

        verify(indexMock, times(1)).retrievePolicies((any()));
        assertThat(result, is(resultMock));
    }


    @Test
    void testConstructAndRetrieveWithResult() {
        //WHEN
        var policyElementMock = mock(PolicyElement.class);
        when(policyElementMock.getSaplName()).thenReturn("SAPL");
        //        when(policyElementMock.getClass()).thenCallRealMethod();

        var documentMock = mock(SAPL.class);
        when(documentMock.getPolicyElement()).thenReturn(policyElementMock);

        var policyRetrievalResult = new PolicyRetrievalResult().withMatch(documentMock);
        //        doReturn(Collections.singletonList(documentMock)).when(resultMock.getMatchingDocuments());

        when(indexMock.retrievePolicies(any())).thenReturn(Mono.just(policyRetrievalResult));

        //DO
        val prp = new GenericInMemoryIndexedPolicyRetrievalPoint(indexMock, sourceMock);
        val result = prp.retrievePolicies(contextMock).blockFirst();
        prp.dispose();

        //THEN
        verify(sourceMock, times(1)).getUpdates();
        verify(indexMock, times(1)).apply(any());
        assertThat(prp, is(notNullValue()));

        verify(indexMock, times(1)).retrievePolicies((any()));
        assertThat(result, is(policyRetrievalResult));


    }

}
