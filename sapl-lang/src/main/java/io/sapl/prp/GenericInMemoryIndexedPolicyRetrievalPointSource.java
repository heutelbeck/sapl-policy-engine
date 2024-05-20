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

import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class GenericInMemoryIndexedPolicyRetrievalPointSource implements PolicyRetrievalPointSource {

    private final Flux<UpdateEventDrivenPolicyRetrievalPoint> index;

    private final Disposable indexSubscription;

    private final PrpUpdateEventSource eventSource;

    public GenericInMemoryIndexedPolicyRetrievalPointSource(UpdateEventDrivenPolicyRetrievalPoint seedIndex,
            PrpUpdateEventSource eventSource) {
        this.eventSource = eventSource;
        index            = Flux.from(eventSource.getUpdates())
                .scan(seedIndex, UpdateEventDrivenPolicyRetrievalPoint::apply).skip(1L).share().cache(1);
        // initial subscription, so that the index starts building upon startup
        indexSubscription = Flux.from(index).subscribe();
    }

    @Override
    public Flux<PolicyRetrievalPoint> policyRetrievalPoint() {
        return index.cast(PolicyRetrievalPoint.class);
    }

    @Override
    public void dispose() {
        indexSubscription.dispose();
        eventSource.dispose();
    }

}
