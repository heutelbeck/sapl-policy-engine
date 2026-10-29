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
package io.sapl.compiler.index.kleene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.kleene.KleeneDdIndex.And;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Atom;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Constant;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Formula;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Matches;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Not;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Or;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Truth;
import lombok.val;

/**
 * Test adapter from compiled applicability to the independent DD. Predicate
 * identity and expression extraction come from the engine, while construction
 * and traversal do not. This compares logical classifications, not diagnostics
 * or incremental delivery, and deliberately does not implement PolicyIndex.
 */
record CompiledKleeneDdIndex(KleeneDdIndex diagram, List<IndexPredicate> predicates) {

    CompiledKleeneDdIndex {
        predicates = List.copyOf(predicates);
    }

    static CompiledKleeneDdIndex create(List<CompiledDocument> documents, int maxNodes) {
        val identities = new LinkedHashMap<IndexPredicate, Integer>();
        val formulas   = new ArrayList<Formula>();
        for (val document : documents) {
            if (document.isApplicable() instanceof Value value) {
                formulas.add(new Constant(classify(value)));
            } else {
                val expression = ((PureOperator) document.isApplicable()).booleanExpression();
                formulas.add(translate(expression, identities));
            }
        }
        val order = IntStream.range(0, identities.size()).boxed().toList();
        return new CompiledKleeneDdIndex(KleeneDdIndex.build(formulas, order, maxNodes),
                List.copyOf(identities.keySet()));
    }

    Matches lookup(EvaluationContext context) {
        return diagram.lookup(predicate -> classify(predicates.get(predicate).operator().evaluate(context)));
    }

    private static Truth classify(Value value) {
        if (value instanceof BooleanValue(var outcome)) {
            return outcome ? Truth.TRUE : Truth.FALSE;
        }
        return Truth.ERROR;
    }

    private static Formula translate(BooleanExpression expression, Map<IndexPredicate, Integer> identities) {
        return switch (expression) {
        case BooleanExpression.Constant(var outcome) -> new Constant(outcome ? Truth.TRUE : Truth.FALSE);
        case BooleanExpression.Atom(var predicate)   ->
            new Atom(identities.computeIfAbsent(predicate, ignored -> identities.size()));
        case BooleanExpression.Not(var operand)      -> new Not(translate(operand, identities));
        case BooleanExpression.And(var operands)     ->
            new And(operands.stream().map(operand -> translate(operand, identities)).toList());
        case BooleanExpression.Or(var operands)      ->
            new Or(operands.stream().map(operand -> translate(operand, identities)).toList());
        };
    }
}
