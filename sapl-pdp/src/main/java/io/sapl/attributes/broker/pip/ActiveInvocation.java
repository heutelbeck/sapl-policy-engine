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
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker.BrokerSubscription;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The broker's runtime handle for one canonical invocation. Holds the
 * current value, the subscriber index, and the refcount; routes
 * values from a source (a PIP or the fallback repository) to attached
 * consumers.
 * <p>
 * Two variants:
 * <ul>
 * <li>{@link ActivePolicyInformationPointInvocation} — fed by a PIP
 * from the catalog (the normal case).</li>
 * <li>{@link ActiveRepositoryInvocation} — fed by the broker's
 * fallback repository for invocations that have no matching PIP.</li>
 * </ul>
 * <p>
 * Subscriber index, refcount, and snapshot semantics are uniform
 * across both. The PIP broker treats them interchangeably for
 * dispatch and lifecycle.
 */
sealed interface ActiveInvocation permits ActivePolicyInformationPointInvocation, ActiveRepositoryInvocation {

    long id();

    AttributeFinderInvocation invocation();

    /** {@code null} for repository-fed active invocations (no PIP spec). */
    @Nullable
    StreamAttributeFinderSpecification sourceSpec();

    Optional<Value> snapshot();

    int attach(BrokerSubscription subscriber);

    int detach(BrokerSubscription subscriber);

    Set<BrokerSubscription> subscribers();

    int refcount();

    boolean isClosed();

    /** Begin delivering values. No-op if already started or closed. */
    void start();

    /** Idempotent. Releases the underlying source. */
    void close();
}
