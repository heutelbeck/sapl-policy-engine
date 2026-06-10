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
package io.sapl.api.pdp;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Decision artefact passed to {@link DecisionInterceptor} observers.
 * <p>
 * Required surface: the {@link AuthorizationDecision} itself, the
 * structured {@link #trace()} (computed lazily by implementations), and
 * the errors observed during evaluation. Round-level inputs
 * ({@link #dependencies()}, {@link #readSnapshot()}) default to empty
 * for implementations that do not capture them.
 *
 * @since 4.1.0
 */
public interface TracedDecision {

    /**
     * @return the authorization decision this artefact carries
     */
    AuthorizationDecision authorizationDecision();

    /**
     * @return a structured trace of the evaluation that produced the
     * decision. Implementations build this on demand; cheap to call
     * once, computed once.
     */
    Value trace();

    /**
     * @return errors observed during the evaluation that produced this
     * decision. Empty when the decision is final (PERMIT, DENY,
     * NOT_APPLICABLE, SUSPEND).
     */
    Collection<ErrorValue> errors();

    /**
     * @return the attribute subscriptions touched during evaluation,
     * keyed by subscription key. Empty when the producing evaluation
     * was not streaming or did not capture deps.
     */
    default Map<SubscriptionKey, List<Occurrence>> dependencies() {
        return Map.of();
    }

    /**
     * @return the snapshot value held for each touched subscription at
     * evaluation time. Empty when the producing evaluation did not
     * capture snapshots.
     */
    default Map<SubscriptionKey, AttributeSnapshot> readSnapshot() {
        return Map.of();
    }
}
