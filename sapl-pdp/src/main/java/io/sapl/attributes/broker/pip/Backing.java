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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.ConsumerSubscriptionImpl;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * One value source for one canonical invocation, used by the PIP
 * broker to serve a subscription key.
 * <p>
 * Two variants:
 * <ul>
 * <li>{@link BackingSubscription} — fed by a PIP from the catalog
 * (the normal case).</li>
 * <li>{@link DelegatedBacking} — fed by a fallback broker for keys
 * that have no matching PIP in the catalog.</li>
 * </ul>
 * <p>
 * Subscriber index, refcount, and snapshot semantics are uniform
 * across both. The PIP broker treats them interchangeably for
 * dispatch and lifecycle.
 */
sealed interface Backing permits BackingSubscription, DelegatedBacking {

    long id();

    AttributeFinderInvocation invocation();

    /** {@code null} for delegated backings (no PIP spec backs them). */
    @Nullable
    StreamAttributeFinderSpecification sourceSpec();

    Optional<Value> snapshot();

    int attach(ConsumerSubscriptionImpl subscriber);

    int detach(ConsumerSubscriptionImpl subscriber);

    Set<ConsumerSubscriptionImpl> subscribers();

    int refcount();

    boolean isClosed();

    /** Begin delivering values. No-op if already started or closed. */
    void start();

    /** Idempotent. Releases the underlying source. */
    void close();
}
