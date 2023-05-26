/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.filesystem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
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
import reactor.util.context.Context;

class FilesystemPRPTest {

	@Test
	void call_index_apply_method_for_each_prp_update_event() {
		var mockSource = mock(FileSystemPrpUpdateEventSource.class);
		var mockIndex  = mock(CanonicalImmutableParsedDocumentIndex.class);

		var updateEventFlux = Flux.just(event(Type.PUBLISH), event(Type.WITHDRAW), event(Type.PUBLISH),
				event(Type.WITHDRAW), event(Type.PUBLISH)

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
				.apply(argThat(prpUpdateEvent -> prpUpdateEvent.getUpdates()[0].getType() == Type.WITHDRAW));
	}

	private PrpUpdateEvent event(Type type) {
		return new PrpUpdateEvent(new Update(type, null, null));
	}

	@Test
	void doTest() {
		var interpreter = new DefaultSAPLInterpreter();
		var source      = new FileSystemPrpUpdateEventSource("src/test/resources/policies", interpreter);
		var prp         = new GenericInMemoryIndexedPolicyRetrievalPoint(new NaiveImmutableParsedDocumentIndex(),
				source);
		var authzSub    = AuthorizationSubscription.of("Willi", "eat", "icecream");

		prp.retrievePolicies().contextWrite(ctx -> setUpAuthorizationContext(ctx, authzSub))
				.log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		prp.dispose();
	}

	private static Context setUpAuthorizationContext(Context ctx, AuthorizationSubscription authzSubscription) {
		ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
		ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
		ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
		return ctx;
	}

}
