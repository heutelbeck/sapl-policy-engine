/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.store;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.compiler.eval.AttributeStore;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * In-memory {@link AttributeStore}. Constants and pure-operator policies
 * do not interact with the store; streaming voters that subscribe through
 * this implementation currently fail fast.
 */
public final class InMemoryAttributeStore implements AttributeStore {

    private static final String ERROR_NOT_YET_IMPLEMENTED = "InMemoryAttributeStore.open not yet implemented.";

    @Override
    public Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        // TODO: implement PIP-aware open: mailbox-backed gate, dep diffing
        // against the backing PIP layer, and refire on snapshot growth.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    @Override
    public void close() {
    }
}
