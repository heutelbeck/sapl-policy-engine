/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class GenericInMemoryIndexedPolicyRetrievalPointTest {

	PrpUpdateEventSource sourceMock;

	ImmutableParsedDocumentIndex indexMock;

	@BeforeEach
	void beforeEach() {
		sourceMock = mock(PrpUpdateEventSource.class);
		var eventMock = mock(PrpUpdateEvent.class);
		when(sourceMock.getUpdates()).thenReturn(Flux.just(eventMock));

		indexMock = mock(ImmutableParsedDocumentIndex.class);
		when(indexMock.apply(any())).thenReturn(indexMock);

	}

	@Test
	void testConstructAndRetrieveWithEmptyResult() {
		// WHEN
		var resultMock = mock(PolicyRetrievalResult.class);
		when(indexMock.retrievePolicies()).thenReturn(Mono.just(resultMock));

		// DO
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(indexMock, sourceMock);
		var result = prp.retrievePolicies().blockFirst();
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
		var policyElementMock = mock(PolicyElement.class);
		when(policyElementMock.getSaplName()).thenReturn("SAPL");
		// when(policyElementMock.getClass()).thenCallRealMethod();

		var documentMock = mock(SAPL.class);
		when(documentMock.getPolicyElement()).thenReturn(policyElementMock);

		var policyRetrievalResult = new PolicyRetrievalResult().withMatch(documentMock);
		// doReturn(Collections.singletonList(documentMock)).when(resultMock.getMatchingDocuments());

		when(indexMock.retrievePolicies()).thenReturn(Mono.just(policyRetrievalResult));

		// DO
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(indexMock, sourceMock);
		var result = prp.retrievePolicies().blockFirst();
		prp.dispose();

		// THEN
		verify(sourceMock, times(1)).getUpdates();
		verify(indexMock, times(1)).apply(any());
		assertThat(prp, is(notNullValue()));

		verify(indexMock, times(1)).retrievePolicies();
		assertThat(result, is(policyRetrievalResult));

	}

	@Test
	void testConstructAndRetrieveWithNonSAPLResult() {
		// WHEN
		var policyElementMock = mock(PolicyElement.class);
		when(policyElementMock.getSaplName()).thenReturn("SAPL");
		// when(policyElementMock.getClass()).thenCallRealMethod();

		var documentMock = mock(SAPL.class);

		var policyRetrievalResult = new PolicyRetrievalResult().withMatch(documentMock);
		// doReturn(Collections.singletonList(documentMock)).when(resultMock.getMatchingDocuments());

		when(indexMock.retrievePolicies()).thenReturn(Mono.just(policyRetrievalResult));

		// DO
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(indexMock, sourceMock);
		var result = prp.retrievePolicies().blockFirst();
		prp.dispose();

		// THEN
		verify(sourceMock, times(1)).getUpdates();
		verify(indexMock, times(1)).apply(any());
		assertThat(prp, is(notNullValue()));

		verify(indexMock, times(1)).retrievePolicies();
		assertThat(result, is(policyRetrievalResult));

	}

}
