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

import io.sapl.api.model.*;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class BooleanGuardCompiler {
    public static CompiledExpression applyBooleanGuard(CompiledExpression expression, SourceLocation location,
            String errorMessageTemplate) {
        return switch (expression) {
        case BooleanValue b    -> b;
        case ErrorValue e      -> e;
        case Value v           -> Value.errorAt(location, errorMessageTemplate, v);
        case PureOperator po   -> new PureBooleanTypeCheck(po, location, po.isDependingOnSubscription(),
                po.isRelativeExpression(), errorMessageTemplate);
        case StreamOperator so -> new StreamBooleanTypeCheck(so, location, errorMessageTemplate);
        };
    }

    public record PureBooleanTypeCheck(
            PureOperator operator,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression,
            String errorMessage) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(PureBooleanTypeCheck.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val result = operator.evaluate(ctx);
            return switch (result) {
            case BooleanValue b -> b;
            case ErrorValue e   -> e;
            default             -> Value.errorAt(location, errorMessage.formatted(result));
            };
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, operator.semanticHash());
        }
    }

    public record StreamBooleanTypeCheck(StreamOperator operator, SourceLocation location, String errorMessage)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val r = operator.evaluate(ctx);
            val v = r.result();
            if (v == null || v instanceof BooleanValue || v instanceof ErrorValue) {
                return r;
            }
            return new ExpressionResult(Value.errorAt(location, errorMessage.formatted(v)), r.dependencies());
        }
    }
}
