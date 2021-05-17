/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

class ResourcesPRPTest {

	@Test
	void call_index_apply_method_for_each_prp_update_event() {
		var mockSource = mock(ResourcesPrpUpdateEventSource.class);
		var mockIndex = mock(CanonicalImmutableParsedDocumentIndex.class);

		var updateEventFlux = Flux.just(event(Type.PUBLISH), event(Type.UNPUBLISH), event(Type.PUBLISH),
				event(Type.UNPUBLISH), event(Type.PUBLISH)

		);

		// WHEN
		when(mockSource.getUpdates()).thenReturn(updateEventFlux);
		when(mockIndex.apply(any())).thenReturn(mockIndex);

		// DO
		new GenericInMemoryIndexedPolicyRetrievalPoint(mockIndex, mockSource);

		// THEN
		verify(mockSource, times(1)).getUpdates();
		verify(mockIndex, times(3))
				.apply(argThat(prpUpdateEvent -> prpUpdateEvent.getUpdates()[0].getType() == Type.PUBLISH));
		verify(mockIndex, times(2))
				.apply(argThat(prpUpdateEvent -> prpUpdateEvent.getUpdates()[0].getType() == Type.UNPUBLISH));
	}

	private PrpUpdateEvent event(Type type) {
		return new PrpUpdateEvent(new Update(type, null, null));
	}

	@Test
	void doTest() throws InitializationException  {
		var interpreter = new DefaultSAPLInterpreter();
		var source = new ResourcesPrpUpdateEventSource("/policies", interpreter);
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(new NaiveImmutableParsedDocumentIndex(), source);
		var authzSubscription = AuthorizationSubscription.of("Willi", "write", "icecream");
		var evaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx = evaluationCtx.forAuthorizationSubscription(authzSubscription);
		prp.retrievePolicies(evaluationCtx).log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		prp.dispose();
	}
}
