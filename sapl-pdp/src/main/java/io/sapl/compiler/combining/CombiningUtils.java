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
package io.sapl.compiler.combining;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared utility methods for combining algorithm implementations.
 */
@UtilityClass
public class CombiningUtils {

    /**
     * Appends an element to a list, returning a new immutable list.
     *
     * @param <T> the element type
     * @param list the original list
     * @param newElement the element to append
     * @return a new list with the element appended
     */
    public static <T> List<T> appendToList(List<T> list, T newElement) {
        if (list.isEmpty())
            return List.of(newElement);
        val result = new ArrayList<T>(list.size() + 1);
        result.addAll(list);
        result.add(newElement);
        return result;
    }

    public record StratifiedPolicies(
            List<Vote> foldableVotes,
            List<CompiledDocument> purePolicies,
            List<CompiledDocument> streamPolicies) {}

    public static StratifiedPolicies classifyPoliciesByEvaluationStrategy(
            List<? extends CompiledDocument> compiledPolicies) {
        val foldableVotes  = new ArrayList<Vote>();
        val purePolicies   = new ArrayList<CompiledDocument>();
        val streamPolicies = new ArrayList<CompiledDocument>();

        for (val policy : compiledPolicies) {
            val isApplicable = policy.isApplicable();
            val voter        = policy.voter();

            if (isApplicable instanceof BooleanValue(var b) && !b) {
                continue; // constant FALSE - not applicable, skip
            }

            if (isApplicable instanceof ErrorValue error) {
                foldableVotes.add(Vote.error(error, policy.metadata()));
            } else if (isApplicable instanceof BooleanValue && voter instanceof Vote vote) {
                foldableVotes.add(vote);
            } else if (voter instanceof StreamVoter) {
                streamPolicies.add(policy);
            } else {
                purePolicies.add(policy);
            }
        }
        return new StratifiedPolicies(foldableVotes, purePolicies, streamPolicies);
    }

    public static Value evaluateApplicability(CompiledExpression isApplicableExpression, EvaluationContext ctx) {
        if (isApplicableExpression instanceof Value v) {
            return v;
        }
        return ((PureOperator) isApplicableExpression).evaluate(ctx);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> asTypedList(Object[] array) {
        return (List<T>) (List<?>) Arrays.asList(array);
    }

    /**
     * Converts a concrete decision (PERMIT or DENY) to its corresponding outcome.
     *
     * @param decision the decision to convert (must be PERMIT or DENY)
     * @return Outcome.PERMIT for PERMIT, Outcome.DENY for DENY
     */
    public static Outcome decisionToOutcome(Decision decision) {
        return decision == Decision.PERMIT ? Outcome.PERMIT : Outcome.DENY;
    }

    /**
     * Merges constraints from two authorization decisions with the same
     * entitlement.
     * <p>
     * Assumes transformation uncertainty has been checked before calling.
     *
     * @param a first authorization decision
     * @param b second authorization decision
     * @return merged authorization decision with concatenated obligations/advice
     */
    public static AuthorizationDecision mergeConstraints(AuthorizationDecision a, AuthorizationDecision b) {
        val resourceA   = a.resource();
        val resourceB   = b.resource();
        val resource    = Value.UNDEFINED.equals(resourceA) ? resourceB : resourceA;
        val obligations = a.obligations().append(b.obligations());
        val advice      = a.advice().append(b.advice());
        return new AuthorizationDecision(a.decision(), obligations, advice, resource);
    }

    /**
     * Creates an INDETERMINATE vote with the given outcome and errors.
     *
     * @param outcome the extended indeterminate outcome
     * @param errors the errors that caused the indeterminate result
     * @param contributingVotes the votes that contributed to this result
     * @param voterMetadata metadata for the result
     * @return an INDETERMINATE vote
     */
    public static Vote indeterminateResult(Outcome outcome, List<ErrorValue> errors, List<Vote> contributingVotes,
            VoterMetadata voterMetadata) {
        return new Vote(AuthorizationDecision.INDETERMINATE, errors, List.of(), contributingVotes, voterMetadata,
                outcome);
    }

    /**
     * Combines two outcomes into a single outcome.
     * <p>
     * If both are the same, returns that outcome. If different, returns
     * PERMIT_OR_DENY.
     *
     * @param a first outcome (may be null)
     * @param b second outcome (may be null)
     * @return combined outcome
     */
    public static Outcome combineOutcomes(Outcome a, Outcome b) {
        if (a == null)
            return b;
        if (b == null)
            return a;
        if (a == b)
            return a;
        return Outcome.PERMIT_OR_DENY;
    }

}
