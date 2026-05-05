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

import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.*;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.dnf.Literal;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.CoverageVoter;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.sapl.util.SaplTesting.TEST_LOCATION;

/**
 * Test factory methods for creating index representation objects.
 */
@UtilityClass
public class IndexTestFixtures {

    public static final Map<Long, Value> PREDICATE_RESULTS = new ConcurrentHashMap<>();

    public static IndexPredicate predicate(long hash) {
        return new IndexPredicate(hash, stubOperator(hash));
    }

    public static IndexPredicate configurablePredicate(long hash) {
        return new IndexPredicate(hash, configurableOperator(hash));
    }

    public static Literal positiveLiteral(long hash) {
        return new Literal(predicate(hash), false);
    }

    public static Literal negativeLiteral(long hash) {
        return new Literal(predicate(hash), true);
    }

    public static Atom atom(long hash) {
        return new Atom(predicate(hash));
    }

    public static CompiledDocument stubDocument(String name) {
        return stubCompiledPolicy(name, Value.TRUE);
    }

    public static CompiledDocument stubDocumentWithApplicability(String name, PureOperator isApplicable) {
        return stubCompiledPolicy(name, isApplicable);
    }

    public static CompiledDocument stubDocumentWithConstantApplicability(String name, Value isApplicable) {
        return stubCompiledPolicy(name, isApplicable);
    }

    private static CompiledPolicy stubCompiledPolicy(String name, CompiledExpression isApplicable) {
        val metadata      = stubMetadata(name);
        val voter         = Vote.abstain(metadata);
        val coverageVoter = new CoverageVoter.Lazy(List.of(), voter, metadata);
        return new CompiledPolicy(isApplicable, voter, voter, coverageVoter, metadata);
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

}
