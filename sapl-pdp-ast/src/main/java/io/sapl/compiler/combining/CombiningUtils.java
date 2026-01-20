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
import io.sapl.compiler.pdp.CompiledDocument;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
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

            if (isApplicable instanceof Value constantApplicable) {
                if (constantApplicable instanceof BooleanValue(var b) && !b) {
                    continue; // constant FALSE - not applicable, skip
                }
                // constant TRUE or ERROR
                if (constantApplicable instanceof ErrorValue error) {
                    foldableVotes.add(Vote.error(error, policy.metadata())); // constant INDETERMINATE
                    continue;
                }
                if (voter instanceof Vote vote) {
                    foldableVotes.add(vote);
                } else if (voter instanceof StreamVoter) {
                    streamPolicies.add(policy);
                } else {
                    purePolicies.add(policy);
                }
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

}
