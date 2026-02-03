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
package io.sapl.compiler.policy;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Lifts operators to a common stratum when strata-ordered evaluation is not
 * optimized.
 * <p>
 * Used in cases where the highest stratum dominates and special-case
 * optimization is not
 * worthwhile (e.g., combining policy constraints where streams are rare).
 */
@UtilityClass
public class OperatorLiftUtil {

    private static final String ERROR_UNEXPECTED_LIFT = "Unexpected expression type during stratum lifting: %s. Indicates an implementation bug.";

    /**
     * Lifts a value or pure operator to pure stratum.
     *
     * @param expr the expression to lift
     * @param location the source location for error reporting
     * @return a pure operator
     */
    public static PureOperator liftToPure(CompiledExpression expr, SourceLocation location) {
        return switch (expr) {
        case Value v        -> new ConstantPure(v, location);
        case PureOperator p -> p;
        default             -> throw new SaplCompilerException(ERROR_UNEXPECTED_LIFT.formatted(expr), location);
        };
    }

    /**
     * Lifts any expression to stream stratum.
     *
     * @param expr the expression to lift
     * @return a stream operator
     */
    public static StreamOperator liftToStream(CompiledExpression expr) {
        return switch (expr) {
        case Value v          -> new ConstantStream(v);
        case PureOperator p   -> new PureToStream(p);
        case StreamOperator s -> s;
        };
    }

    /**
     * Wraps a constant value as a pure operator.
     */
    record ConstantPure(Value value, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return value;
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }
    }

    /**
     * Wraps a constant value as a stream operator emitting a single value.
     */
    record ConstantStream(Value value) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.just(new TracedValue(value, List.of()));
        }
    }

    /**
     * Wraps a pure operator as a stream operator evaluating once per subscription.
     */
    record PureToStream(PureOperator pure) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx = ctxView.get(EvaluationContext.class);
                return Flux.just(new TracedValue(pure.evaluate(evalCtx), List.of()));
            });
        }
    }
}
