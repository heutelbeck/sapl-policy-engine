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
package io.sapl.compiler.index.smtdd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryPureValue;
import io.sapl.compiler.expressions.BinaryOperationCompiler.BinaryValuePure;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler.PureBooleanTypeCheck;
import lombok.experimental.UtilityClass;
import lombok.val;

import static io.sapl.util.SaplTesting.TEST_LOCATION;

/**
 * Shared test factory methods for SMTDD tests.
 */
@UtilityClass
class SmtddTestFixtures {

    public static final Map<Long, Value> OPERATOR_RESULTS = new ConcurrentHashMap<>();

    public static IndexPredicate eqPredicate(PureOperator operand, Value constant) {
        val hash                = operand.semanticHash() * 31 + constant.hashCode();
        val equalityOperator    = new BinaryPureValue(BinaryOperatorType.EQ,
                (leftVal, rightVal, loc) -> leftVal.equals(rightVal) ? Value.TRUE : Value.FALSE, operand, constant,
                TEST_LOCATION, operand.isDependingOnSubscription(), false);
        val typeCheckedOperator = new PureBooleanTypeCheck(equalityOperator, TEST_LOCATION, true, false,
                "Expected boolean but got: %s");
        return new IndexPredicate(hash, typeCheckedOperator);
    }

    public static IndexPredicate nePredicate(PureOperator operand, Value constant) {
        val hash                = operand.semanticHash() * 31 + constant.hashCode() + 1;
        val neOperator          = new BinaryPureValue(BinaryOperatorType.NE,
                (leftVal, rightVal, loc) -> !leftVal.equals(rightVal) ? Value.TRUE : Value.FALSE, operand, constant,
                TEST_LOCATION, operand.isDependingOnSubscription(), false);
        val typeCheckedOperator = new PureBooleanTypeCheck(neOperator, TEST_LOCATION, true, false,
                "Expected boolean but got: %s");
        return new IndexPredicate(hash, typeCheckedOperator);
    }

    /**
     * Creates a BinaryPureValue IN predicate: pureOperand IN collection.
     * The collection is the constant (right-hand value).
     */
    public static IndexPredicate inPredicate(PureOperator operand, Value collection) {
        val hash        = operand.semanticHash() * 31 + collection.hashCode() + 2;
        val inOperator  = new BinaryPureValue(BinaryOperatorType.IN, (leftVal, rightVal, loc) -> Value.FALSE, operand,
                collection, TEST_LOCATION, operand.isDependingOnSubscription(), false);
        val typeChecked = new PureBooleanTypeCheck(inOperator, TEST_LOCATION, true, false,
                "Expected boolean but got: %s");
        return new IndexPredicate(hash, typeChecked);
    }

    /**
     * Creates a BinaryValuePure HAS_ONE predicate: container HAS pureOperand.
     * The container (object) is the constant (left-hand value).
     */
    public static IndexPredicate hasPredicate(PureOperator operand, Value container) {
        val hash        = operand.semanticHash() * 31 + container.hashCode() + 3;
        val hasOperator = new BinaryValuePure(BinaryOperatorType.HAS_ONE, (leftVal, rightVal, loc) -> Value.FALSE,
                container, operand, TEST_LOCATION, operand.isDependingOnSubscription(), false);
        val typeChecked = new PureBooleanTypeCheck(hasOperator, TEST_LOCATION, true, false,
                "Expected boolean but got: %s");
        return new IndexPredicate(hash, typeChecked);
    }

    public static PureOperator stubOperand(long hash) {
        return new PureOperator() {
            @Override
            public Value evaluate(EvaluationContext ctx) {
                return OPERATOR_RESULTS.getOrDefault(hash, Value.FALSE);
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

    public static List<List<IndexPredicate>> extractPredicates(List<BooleanExpression> expressions) {
        val result = new ArrayList<List<IndexPredicate>>();
        for (val expression : expressions) {
            val predicates = new ArrayList<IndexPredicate>();
            collectPredicates(expression, predicates);
            result.add(predicates);
        }
        return result;
    }

    static void collectPredicates(BooleanExpression expression, List<IndexPredicate> result) {
        switch (expression) {
        case Constant ignored    -> {}
        case Atom(var predicate) -> {
            if (!result.contains(predicate)) {
                result.add(predicate);
            }
        }
        case Not(var operand)    -> collectPredicates(operand, result);
        case Or(var operands)    -> operands.forEach(op -> collectPredicates(op, result));
        case And(var operands)   -> operands.forEach(op -> collectPredicates(op, result));
        }
    }

}
