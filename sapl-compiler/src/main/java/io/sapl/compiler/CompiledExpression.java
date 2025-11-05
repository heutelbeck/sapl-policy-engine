/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.v2.Value;
import lombok.RequiredArgsConstructor;
import org.eclipse.xtext.xbase.controlflow.EvaluationContext;
import reactor.core.publisher.Flux;

public interface CompiledExpression {

    interface PureCompiledExpression extends CompiledExpression {
        public Value evaluate(EvaluationContext context);
    }

    interface AsyncCompiledExpression extends CompiledExpression {
        public Flux<Value> evaluate(EvaluationContext context);
    }

    @RequiredArgsConstructor
    class Constant implements PureCompiledExpression {
        private final Value value;

        @Override
        public Value evaluate(EvaluationContext context) {
            return value;
        }
    }

    class Operator implements PureCompiledExpression {

        @Override
        public Value evaluate(EvaluationContext context) {
            return null;
        }
    }

}
