/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.resources;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Clock;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ResourcesPRPTests {

    @Test
    void call_index_apply_method_for_each_prp_update_event() {
        final var mockSource = mock(ResourcesPrpUpdateEventSource.class);
        final var mockIndex  = mock(CanonicalImmutableParsedDocumentIndex.class);

        final var updateEventFlux = Flux.just(event(Type.PUBLISH), event(Type.WITHDRAW), event(Type.PUBLISH),
                event(Type.WITHDRAW), event(Type.PUBLISH)

        );

        // WHEN
        when(mockSource.getUpdates()).thenReturn(updateEventFlux);
        when(mockIndex.apply(any())).thenReturn(mockIndex);

        // DO
        new GenericInMemoryIndexedPolicyRetrievalPointSource(mockIndex, mockSource);

        // THEN
        verify(mockSource, times(1)).getUpdates();
        verify(mockIndex, times(3))
                .apply(argThat(prpUpdateEvent -> prpUpdateEvent.getUpdates()[0].getType() == Type.PUBLISH));
        verify(mockIndex, times(2))
                .apply(argThat(prpUpdateEvent -> prpUpdateEvent.getUpdates()[0].getType() == Type.WITHDRAW));
    }

    private PrpUpdateEvent event(Type type) {
        return new PrpUpdateEvent(new Update(type, null));
    }

    @Test
    void doTest() {
        final var interpreter = new DefaultSAPLInterpreter();
        final var source      = new ResourcesPrpUpdateEventSource("/policies", interpreter);
        final var prp         = new GenericInMemoryIndexedPolicyRetrievalPointSource(
                new NaiveImmutableParsedDocumentIndex(), source);
        final var authzSub    = AuthorizationSubscription.of("Willi", "write", "icecream");
        final var sut         = Objects.requireNonNull(prp.policyRetrievalPoint().blockFirst()).retrievePolicies()
                .contextWrite(ctx -> setUpAuthorizationContext(ctx, authzSub));
        StepVerifier.create(sut).expectNextMatches(PolicyRetrievalResult.class::isInstance).verifyComplete();
        prp.dispose();
    }

    private static Context setUpAuthorizationContext(Context ctx, AuthorizationSubscription authzSubscription) {
        ctx = AuthorizationContext.setAttributeStreamBroker(ctx,
                new CachingAttributeStreamBroker(new InMemoryAttributeRepository(Clock.systemUTC())));
        ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
        ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
        return ctx;
    }

}
