/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class SelectorUtil {
    public static Supplier<Flux<Val>> toArrayElementSelector(BiPredicate<Integer, Val> selector) {
        return () -> Flux.deferContextual(ctx -> {
            var relativeNode = AuthorizationContext.getRelativeNode(ctx);
            var index        = AuthorizationContext.getIndex(ctx);
            try {
                return Flux.just(Val.of(selector.test(index, relativeNode)));
            } catch (PolicyEvaluationException e) {
                return Flux.just(Val.error(e.getMessage()));
            }
        });
    }

    public static Supplier<Flux<Val>> toObjectFieldSelector(BiPredicate<String, Val> selector) {
        return () -> Flux.deferContextual(ctx -> {
            var relativeNode = AuthorizationContext.getRelativeNode(ctx);
            var key          = AuthorizationContext.getKey(ctx);
            try {
                return Flux.just(Val.of(selector.test(key, relativeNode)));
            } catch (PolicyEvaluationException e) {
                return Flux.just(Val.error(e.getMessage()));
            }
        });
    }

}
