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

import lombok.NonNull;
import lombok.val;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * A container for multiple authorization decisions, each keyed by the
 * subscription ID. This is the response type for
 * batch authorization requests, providing decisions for all subscriptions in a
 * {@link MultiAuthorizationSubscription}.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * pdp.decideAll(multiSubscription).subscribe(multiDecision -> {
 *     if (multiDecision.isPermitted("read-file")) {
 *         // allow read access
 *     }
 *     if (multiDecision.isPermitted("write-file")) {
 *         // allow write access
 *     }
 * });
 * }</pre>
 */
public class MultiAuthorizationDecision implements Iterable<IdentifiableAuthorizationDecision> {

    private final Map<String, AuthorizationDecision> decisions = new HashMap<>();

    /**
     * Creates an indeterminate multi-decision with a single empty-ID entry.
     *
     * @return an indeterminate multi-decision
     */
    public static MultiAuthorizationDecision indeterminate() {
        val multiDecision = new MultiAuthorizationDecision();
        multiDecision.setDecision("", AuthorizationDecision.INDETERMINATE);
        return multiDecision;
    }

    /**
     * Sets the authorization decision for a subscription.
     *
     * @param subscriptionId
     * the subscription ID
     * @param decision
     * the authorization decision
     */
    public void setDecision(@NonNull String subscriptionId, @NonNull AuthorizationDecision decision) {
        decisions.put(subscriptionId, decision);
    }

    /**
     * Gets the authorization decision for a subscription.
     *
     * @param subscriptionId
     * the subscription ID
     *
     * @return the authorization decision, or null if not found
     */
    public AuthorizationDecision getDecision(String subscriptionId) {
        return decisions.get(subscriptionId);
    }

    /**
     * Gets the decision type for a subscription.
     *
     * @param subscriptionId
     * the subscription ID
     *
     * @return the decision type, or null if not found
     */
    public Decision getDecisionType(String subscriptionId) {
        val decision = decisions.get(subscriptionId);
        return decision == null ? null : decision.decision();
    }

    /**
     * Checks if the decision for a subscription is PERMIT.
     *
     * @param subscriptionId
     * the subscription ID
     *
     * @return true if the decision is PERMIT, false otherwise
     */
    public boolean isPermitted(String subscriptionId) {
        val decision = decisions.get(subscriptionId);
        return decision != null && decision.decision() == Decision.PERMIT;
    }

    /**
     * Returns the number of decisions in this container.
     *
     * @return decision count
     */
    public int size() {
        return decisions.size();
    }

    @Override
    public Iterator<IdentifiableAuthorizationDecision> iterator() {
        val entryIterator = decisions.entrySet().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entryIterator.hasNext();
            }

            @Override
            public IdentifiableAuthorizationDecision next() {
                val entry = entryIterator.next();
                return new IdentifiableAuthorizationDecision(entry.getKey(), entry.getValue());
            }
        };
    }

    @Override
    public String toString() {
        val builder = new StringBuilder("MultiAuthorizationDecision {");
        for (val identifiable : this) {
            val decision = identifiable.decision();
            builder.append("\n\t[ID: ").append(identifiable.subscriptionId()).append(" | DECISION: ")
                    .append(decision.decision()).append(" | RESOURCE: ").append(decision.resource())
                    .append(" | OBLIGATIONS: ").append(decision.obligations()).append(" | ADVICE: ")
                    .append(decision.advice()).append(']');
        }
        builder.append("\n}");
        return builder.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MultiAuthorizationDecision other)) {
            return false;
        }
        return Objects.equals(decisions, other.decisions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decisions);
    }
}
