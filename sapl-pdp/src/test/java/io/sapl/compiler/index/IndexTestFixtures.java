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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.BooleanExpression.Atom;
import lombok.experimental.UtilityClass;

import static io.sapl.util.SaplTesting.TEST_LOCATION;

/**
 * Test factory methods for creating index representation objects.
 */
@UtilityClass
class IndexTestFixtures {

    static IndexPredicate predicate(long hash) {
        return new IndexPredicate(hash, stubOperator(hash));
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

}
