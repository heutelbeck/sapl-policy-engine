/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.impl.util.TargetExpressionUtil;
import reactor.core.publisher.Flux;

/**
 * Implements the lazy boolean AND operator, written as '&amp;&amp;' in
 * Expressions.
 * <p>
 * Grammar: Multiplication returns Expression: Comparison (({And.left=current}
 * '&amp;&amp;') right=Comparison)* ;
 */
public class AndImplCustom extends AndImpl {

    private static final String LAZY_OPERATOR_IN_TARGET_ERROR = "Lazy AND operator is not allowed in the target";

    @Override
    public Flux<Val> evaluate() {
        if (TargetExpressionUtil.isInTargetExpression(this)) {
            // indexing implies: lazy evaluation is not allowed in target expressions.
            return Flux.just(Val.error(LAZY_OPERATOR_IN_TARGET_ERROR).withTrace(And.class));
        }
        var left = getLeft().evaluate().map(Val::requireBoolean);
        return left.switchMap(leftResult -> {
            if (leftResult.isError()) {
                return Flux.just(leftResult.withTrace(And.class, Map.of(Trace.LEFT, leftResult)));
            }
            // Lazy evaluation of the right expression
            if (Boolean.TRUE.equals(leftResult.getBoolean())) {
                return getRight().evaluate().map(Val::requireBoolean).map(rightResult -> rightResult
                        .withTrace(And.class, Map.of(Trace.LEFT, leftResult, Trace.RIGHT, rightResult)));
            }
            return Flux.just(Val.FALSE);
        });
    }

}
