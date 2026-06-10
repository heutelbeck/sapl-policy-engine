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
package io.sapl.compiler.expressions;

import io.sapl.api.model.*;
import lombok.val;

import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Binary op functional interface.
 * <p>
 * JIT optimization: Because the implementations are a closed set,
 * the JVM can use a type-check + jump table instead of indirect dispatch.
 * This achieves near-VIRTUAL_INLINED performance without record explosion.
 * <p>
 * Each implementor is the apply function. The default {@link #evalEager}
 * method drives a binary stream-operator node end-to-end against its
 * children: walks left and right via
 * {@link io.sapl.api.model.StreamOperator#evalChild} accumulating
 * subscriptions from any stream children, holds the first
 * {@link ErrorValue}, propagates {@code null} (incomplete child) at the
 * end, then calls {@link #apply}.
 */
@FunctionalInterface
public interface BinaryOperation {
    Value apply(Value left, Value right, SourceLocation location);

    /**
     * Eager binary evaluation: walks both children to accumulate the
     * maximum subscription set, holds the first {@link ErrorValue},
     * returns it after the full walk. {@code null} from a child sets the
     * incomplete flag; on a clean walk with no error, applies the op.
     * Precedence at the end: error &gt; null &gt; applied result.
     *
     * @param left the left operand expression
     * @param right the right operand expression
     * @param location the source location for error reporting
     * @param ctx the evaluation context
     * @return the evaluation result with accumulated subscriptions
     */
    default ExpressionResult evalEager(CompiledExpression left, CompiledExpression right, SourceLocation location,
            EvaluationContext ctx) {
        val     deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
        Value   firstError = null;
        boolean seenNull   = false;
        val     lv         = evalChild(left, ctx, deps);
        if (lv == null) {
            seenNull = true;
        } else if (lv instanceof ErrorValue) {
            firstError = lv;
        }
        val rv = evalChild(right, ctx, deps);
        if (rv == null) {
            seenNull = true;
        } else if (rv instanceof ErrorValue && firstError == null) {
            firstError = rv;
        }
        if (firstError != null) {
            return new ExpressionResult(firstError, deps);
        }
        if (seenNull) {
            return new ExpressionResult(null, deps);
        }
        return new ExpressionResult(apply(lv, rv, location), deps);
    }
}
