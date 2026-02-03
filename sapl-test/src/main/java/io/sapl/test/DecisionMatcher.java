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
package io.sapl.test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Matcher for AuthorizationDecision with fluent chainable API.
 * <p>
 * Create instances via static factory methods in {@link Matchers}:
 *
 * <pre>{@code
 * isPermit()
 * isDeny().containsObligation(logObligation)
 * isPermit().withResource(transformedResource).containsAdvice(hint)
 * }</pre>
 * <p>
 * Matching semantics:
 * <ul>
 * <li>Decision type must match exactly</li>
 * <li>Resource match is exact equality when specified</li>
 * <li>Obligation/advice checks use containment (the decision must contain all
 * specified values)</li>
 * </ul>
 */
public final class DecisionMatcher implements Predicate<AuthorizationDecision> {

    private final Decision               expectedDecision;
    private final List<Value>            expectedObligations  = new ArrayList<>();
    private final List<Value>            expectedAdvice       = new ArrayList<>();
    private Value                        expectedResource     = null;
    private final List<Predicate<Value>> obligationPredicates = new ArrayList<>();
    private final List<Predicate<Value>> advicePredicates     = new ArrayList<>();

    /**
     * Creates a matcher for the given decision type.
     *
     * @param decision the expected decision type
     */
    public DecisionMatcher(@NonNull Decision decision) {
        this.expectedDecision = decision;
    }

    /**
     * Requires the decision to contain the specified obligation.
     * <p>
     * Multiple calls are cumulative - all specified obligations must be present.
     *
     * @param obligation the obligation value that must be present
     * @return this matcher for chaining
     */
    public DecisionMatcher containsObligation(@NonNull Value obligation) {
        expectedObligations.add(obligation);
        return this;
    }

    /**
     * Requires the decision to contain all specified obligations.
     *
     * @param obligations the obligation values that must all be present
     * @return this matcher for chaining
     */
    public DecisionMatcher containsObligations(@NonNull Value... obligations) {
        expectedObligations.addAll(Arrays.asList(obligations));
        return this;
    }

    /**
     * Requires the decision to contain an obligation matching the predicate.
     *
     * @param predicate the predicate to match against obligations
     * @return this matcher for chaining
     */
    public DecisionMatcher containsObligationMatching(@NonNull Predicate<Value> predicate) {
        obligationPredicates.add(predicate);
        return this;
    }

    /**
     * Requires the decision to contain the specified advice.
     * <p>
     * Multiple calls are cumulative - all specified advice must be present.
     *
     * @param advice the advice value that must be present
     * @return this matcher for chaining
     */
    public DecisionMatcher containsAdvice(@NonNull Value advice) {
        expectedAdvice.add(advice);
        return this;
    }

    /**
     * Requires the decision to contain all specified advice.
     *
     * @param advices the advice values that must all be present
     * @return this matcher for chaining
     */
    public DecisionMatcher containsAdvices(@NonNull Value... advices) {
        expectedAdvice.addAll(Arrays.asList(advices));
        return this;
    }

    /**
     * Requires the decision to contain an advice matching the predicate.
     *
     * @param predicate the predicate to match against advice
     * @return this matcher for chaining
     */
    public DecisionMatcher containsAdviceMatching(@NonNull Predicate<Value> predicate) {
        advicePredicates.add(predicate);
        return this;
    }

    /**
     * Requires the decision to have the specified resource (exact match).
     *
     * @param resource the expected resource value
     * @return this matcher for chaining
     */
    public DecisionMatcher withResource(@NonNull Value resource) {
        this.expectedResource = resource;
        return this;
    }

    @Override
    public boolean test(AuthorizationDecision decision) {
        if (decision == null || decision.decision() != expectedDecision) {
            return false;
        }
        if (expectedResource != null && !expectedResource.equals(decision.resource())) {
            return false;
        }
        return matchesObligations(decision) && matchesAdvice(decision);
    }

    private boolean matchesObligations(AuthorizationDecision decision) {
        var actualObligations = decision.obligations();
        for (var expected : expectedObligations) {
            if (doesNotContainValue(actualObligations, expected)) {
                return false;
            }
        }
        for (var predicate : obligationPredicates) {
            if (!anyValueMatchesPredicate(actualObligations, predicate)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAdvice(AuthorizationDecision decision) {
        var actualAdvice = decision.advice();
        for (var expected : expectedAdvice) {
            if (doesNotContainValue(actualAdvice, expected)) {
                return false;
            }
        }
        for (var predicate : advicePredicates) {
            if (!anyValueMatchesPredicate(actualAdvice, predicate)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyValueMatchesPredicate(List<Value> values, Predicate<Value> predicate) {
        for (var value : values) {
            if (predicate.test(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean doesNotContainValue(List<Value> list, Value expected) {
        for (var value : list) {
            if (expected.equals(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a human-readable description of what this matcher expects.
     *
     * @return description of expected decision
     */
    public String describe() {
        var joiner = new StringJoiner(", ", expectedDecision.name() + " decision", "");
        if (expectedResource != null) {
            joiner.add(" with resource " + expectedResource);
        }
        if (!expectedObligations.isEmpty()) {
            joiner.add(" containing obligations " + expectedObligations);
        }
        if (!obligationPredicates.isEmpty()) {
            joiner.add(" containing " + obligationPredicates.size() + " obligation predicate(s)");
        }
        if (!expectedAdvice.isEmpty()) {
            joiner.add(" containing advice " + expectedAdvice);
        }
        if (!advicePredicates.isEmpty()) {
            joiner.add(" containing " + advicePredicates.size() + " advice predicate(s)");
        }
        return joiner.toString();
    }

    /**
     * Returns a description of why the matcher failed for the given decision.
     *
     * @param decision the decision that failed to match
     * @return description of the mismatch
     */
    public String describeMismatch(AuthorizationDecision decision) {
        if (decision == null) {
            return "decision was null";
        }

        var reasons = new ArrayList<String>();

        if (decision.decision() != expectedDecision) {
            reasons.add("expected %s but was %s".formatted(expectedDecision, decision.decision()));
        }

        if (expectedResource != null && !expectedResource.equals(decision.resource())) {
            reasons.add("expected resource %s but was %s".formatted(expectedResource, decision.resource()));
        }

        for (var expected : expectedObligations) {
            if (doesNotContainValue(decision.obligations(), expected)) {
                reasons.add("missing obligation %s".formatted(expected));
            }
        }

        for (var expected : expectedAdvice) {
            if (doesNotContainValue(decision.advice(), expected)) {
                reasons.add("missing advice %s".formatted(expected));
            }
        }

        return reasons.isEmpty() ? "unknown mismatch" : String.join("; ", reasons);
    }
}
