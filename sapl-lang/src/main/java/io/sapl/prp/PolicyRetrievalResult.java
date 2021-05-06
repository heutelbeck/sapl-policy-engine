/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.common.collect.Sets;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.SAPL;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRetrievalResult {

    Collection<? extends AuthorizationDecisionEvaluable> matchingDocuments = new ArrayList<>();
    @Getter
    boolean errorsInTarget = false;
    @Getter
    boolean prpValidState = true;

    public Collection<? extends AuthorizationDecisionEvaluable> getMatchingDocuments() {
        return this.matchingDocuments;
    }

    public PolicyRetrievalResult withMatch(AuthorizationDecisionEvaluable match) {
        var matches = new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments);
        matches.add(match);
        return new PolicyRetrievalResult(matches, errorsInTarget, prpValidState);
    }

    public PolicyRetrievalResult withError() {
        return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), true, prpValidState);
    }

    public PolicyRetrievalResult withInvalidState() {
        return new PolicyRetrievalResult(new ArrayList<AuthorizationDecisionEvaluable>(matchingDocuments), errorsInTarget, false);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || other.getClass() != this.getClass()) {
            return false;
        }
        final PolicyRetrievalResult otherResult = (PolicyRetrievalResult) other;
        if (!areEqual(this.getMatchingDocuments(), otherResult.getMatchingDocuments())) {
            return false;
        }
        return this.isErrorsInTarget() == otherResult.isErrorsInTarget();
    }

    private static boolean areEqual(Collection<? extends AuthorizationDecisionEvaluable> thisMatchingDocuments,
                                    Collection<? extends AuthorizationDecisionEvaluable> otherMatchingDocuments) {
        if (thisMatchingDocuments == null) {
            return otherMatchingDocuments == null;
        }
        if (otherMatchingDocuments == null) {
            return false;
        }
        if (thisMatchingDocuments.size() != otherMatchingDocuments.size()) {
            return false;
        }
        final Iterator<? extends AuthorizationDecisionEvaluable> thisIterator = thisMatchingDocuments.iterator();
        final Iterator<? extends AuthorizationDecisionEvaluable> otherIterator = otherMatchingDocuments.iterator();
        while (thisIterator.hasNext()) {
            if (!EcoreUtil.equals(thisIterator.next(), otherIterator.next())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        var prime = 59;
        var result = 1;
        final Collection<? extends AuthorizationDecisionEvaluable> thisMatchingDocuments = getMatchingDocuments();
        result = result * prime + (thisMatchingDocuments == null ? 43 : thisMatchingDocuments.hashCode());
        result = result * prime + (isErrorsInTarget() ? 79 : 97);
        return result;
    }

    @Override
    public String toString() {
        return "PolicyRetrievalResult(" + "matchingDocuments=" + getMatchingDocuments() + ", errorsInTarget="
                + isErrorsInTarget() + ")";
    }

    public static class PolicyRetrievalResultCollector<T extends Pair<SAPL, Val>> implements Collector<T, Builder, PolicyRetrievalResult> {

        @Override
        public Supplier<Builder> supplier() {
            return Builder::builder;
        }

        @Override
        public BiConsumer<Builder, T> accumulator() {
            return Builder::add;
        }

        @Override
        public BinaryOperator<Builder> combiner() {
            return (left, right) -> left.combine(right.build());
        }

        @Override
        public Function<Builder, PolicyRetrievalResult> finisher() {
            return Builder::build;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.immutableEnumSet(Characteristics.UNORDERED);
        }

        public static <T extends Pair<SAPL, Val>> PolicyRetrievalResultCollector<T> toResult() {
            return new PolicyRetrievalResultCollector<>();
        }
    }

    private static class Builder {

        private PolicyRetrievalResult result = new PolicyRetrievalResult();

        public static Builder builder() {
            return new Builder();
        }

        public static void add(Builder policyRetrievalResultBuilder, Pair<SAPL, Val> pair) {
            policyRetrievalResultBuilder.addPair(pair);
        }

        private void addPair(Pair<SAPL, Val> pair) {
            var document = pair.getKey();
            var match = pair.getValue();

            if (match.isError()) {
                result = result.withError();
            }
            if (!match.isBoolean()) {
                log.error("matching returned error. (Should never happen): {}", match.getMessage());
                result = result.withError();
            }
            if (match.getBoolean()) {
                result = result.withMatch(document);
            }
        }

        public PolicyRetrievalResult build() {
            return result;
        }

        public Builder combine(PolicyRetrievalResult build) {
            build.getMatchingDocuments().forEach(result::withMatch);
            return this;
        }
    }

}
