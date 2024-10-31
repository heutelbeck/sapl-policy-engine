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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class GenericInMemoryIndexedPolicyRetrievalPointSourceTests {
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    PrpUpdateEventSource                  sourceMock;
    UpdateEventDrivenPolicyRetrievalPoint indexMock;

    @BeforeEach
    void beforeEach() {
        sourceMock = mock(PrpUpdateEventSource.class);
        final var eventMock = mock(PrpUpdateEvent.class);
        when(sourceMock.getUpdates()).thenReturn(Flux.just(eventMock));

        indexMock = mock(UpdateEventDrivenPolicyRetrievalPoint.class);
        when(indexMock.apply(any())).thenReturn(indexMock);
    }

    @Test
    void testConstructAndRetrieveWithEmptyResult() {
        // WHEN
        final var resultMock = mock(PolicyRetrievalResult.class);
        when(indexMock.retrievePolicies()).thenReturn(Mono.just(resultMock));

        // DO
        final var prp    = new GenericInMemoryIndexedPolicyRetrievalPointSource(indexMock, sourceMock);
        final var result = prp.policyRetrievalPoint().flatMap(PolicyRetrievalPoint::retrievePolicies).blockFirst();
        prp.dispose();

        // THEN
        verify(sourceMock, times(1)).getUpdates();
        verify(indexMock, times(1)).apply(any());
        assertThat(prp, is(notNullValue()));

        verify(indexMock, times(1)).retrievePolicies();
        assertThat(result, is(resultMock));
    }

    @Test
    void testConstructAndRetrieveWithResult() {
        // WHEN
        final var doc                   = INTERPRETER.parseDocument("policy \"x\" permit");
        final var policyRetrievalResult = new PolicyRetrievalResult().withMatch(new DocumentMatch(doc, Val.TRUE));
        when(indexMock.retrievePolicies()).thenReturn(Mono.just(policyRetrievalResult));

        // DO
        final var prp    = new GenericInMemoryIndexedPolicyRetrievalPointSource(indexMock, sourceMock);
        final var result = prp.policyRetrievalPoint().flatMap(PolicyRetrievalPoint::retrievePolicies).blockFirst();
        prp.dispose();

        // THEN
        verify(sourceMock, times(1)).getUpdates();
        verify(indexMock, times(1)).apply(any());
        assertThat(prp, is(notNullValue()));

        verify(indexMock, times(1)).retrievePolicies();
        assertThat(result, is(policyRetrievalResult));

    }

}
