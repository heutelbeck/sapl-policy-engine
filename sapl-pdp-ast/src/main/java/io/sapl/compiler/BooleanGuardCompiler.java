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
package io.sapl.compiler;

import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

@UtilityClass
public class BooleanGuardCompiler {
    public static CompiledExpression applyBooleanGuard(CompiledExpression expression, SourceLocation location,
            String errorMessageTemplate) {
        return switch (expression) {
        case BooleanValue b    -> b;
        case ErrorValue e      -> e;
        case Value v           -> Value.errorAt(location, errorMessageTemplate, v);
        case PureOperator po   ->
            new PureBooleanTypeCheck(po, location, po.isDependingOnSubscription(), errorMessageTemplate);
        case StreamOperator so -> new StreamBooleanTypeCheck(so, location, errorMessageTemplate);
        };
    }

    public record PureBooleanTypeCheck(
            PureOperator operator,
            SourceLocation location,
            boolean isDependingOnSubscription,
            String errorMessage) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val result = operator.evaluate(ctx);
            return switch (result) {
            case BooleanValue b -> b;
            case ErrorValue e   -> e;
            default             -> Value.errorAt(location, errorMessage.formatted(result));
            };
        }
    }

    public record StreamBooleanTypeCheck(StreamOperator operator, SourceLocation location, String errorMessage)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return operator.stream().map(result -> switch (result.value()) {
            case BooleanValue b -> result;
            case ErrorValue e   -> result;
            default             -> new TracedValue(Value.errorAt(location, errorMessage.formatted(result.value())),
                    result.contributingAttributes());
            });
        }
    }
}
