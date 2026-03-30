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
package io.sapl.compiler.index;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.IndexPredicate;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import static io.sapl.util.SaplTesting.TEST_LOCATION;

/**
 * Test factory methods for creating index representation objects.
 */
@UtilityClass
public class IndexTestFixtures {

    public static final Map<Long, Value> PREDICATE_RESULTS = new ConcurrentHashMap<>();

    static IndexPredicate predicate(long hash) {
        return new IndexPredicate(hash, stubOperator(hash));
    }

    public static IndexPredicate configurablePredicate(long hash) {
        return new IndexPredicate(hash, configurableOperator(hash));
    }

    static Literal positiveLiteral(long hash) {
        return new Literal(predicate(hash), false);
    }

    static Literal negativeLiteral(long hash) {
        return new Literal(predicate(hash), true);
    }

    static Atom atom(long hash) {
        return new Atom(predicate(hash));
    }

    public static CompiledDocument stubDocument(String name) {
        return new StubDocument(name, Value.TRUE, Vote.abstain(stubMetadata(name)));
    }

    static VoterMetadata stubMetadata(String name) {
        return new VoterMetadata() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String pdpId() {
                return "testPdp";
            }

            @Override
            public String configurationId() {
                return "testConfig";
            }

            @Override
            public Outcome outcome() {
                return Outcome.PERMIT;
            }

            @Override
            public boolean hasConstraints() {
                return false;
            }
        };
    }

    private static PureOperator stubOperator(long hash) {
        return new PureOperator() {
            @Override
            public Value evaluate(EvaluationContext ctx) {
                return Value.TRUE;
            }

            @Override
            public SourceLocation location() {
                return TEST_LOCATION;
            }

            @Override
            public boolean isDependingOnSubscription() {
                return true;
            }

            @Override
            public long semanticHash() {
                return hash;
            }
        };
    }

    private static PureOperator configurableOperator(long hash) {
        return new PureOperator() {
            @Override
            public Value evaluate(EvaluationContext ctx) {
                return PREDICATE_RESULTS.getOrDefault(hash, Value.FALSE);
            }

            @Override
            public SourceLocation location() {
                return TEST_LOCATION;
            }

            @Override
            public boolean isDependingOnSubscription() {
                return true;
            }

            @Override
            public long semanticHash() {
                return hash;
            }
        };
    }

    private record StubDocument(String name, CompiledExpression isApplicable, Voter voter) implements CompiledDocument {
        @Override
        public VoterMetadata metadata() {
            return stubMetadata(name);
        }

        @Override
        public Voter applicabilityAndVote() {
            return voter;
        }

        @Override
        public Flux<VoteWithCoverage> coverage() {
            return Flux.empty();
        }
    }

}
