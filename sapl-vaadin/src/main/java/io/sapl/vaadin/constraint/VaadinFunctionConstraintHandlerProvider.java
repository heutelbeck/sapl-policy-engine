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
package io.sapl.vaadin.constraint;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import reactor.core.publisher.Mono;

public interface VaadinFunctionConstraintHandlerProvider {
    boolean isResponsible(Value constraint);

    Function<UI, Mono<Boolean>> getHandler(Value constraint);

    static VaadinFunctionConstraintHandlerProvider of(Predicate<Value> isResponsible,
            Function<UI, Mono<Boolean>> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return isResponsible.test(constraint);
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(Value constraint) {
                return handler;
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(ObjectValue constraintFilter, Consumer<Value> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                if (!(constraint instanceof ObjectValue objectConstraint)) {
                    return false;
                }
                for (var entry : constraintFilter.entrySet()) {
                    var actual = objectConstraint.get(entry.getKey());
                    if (!entry.getValue().equals(actual)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(Value constraint) {
                return ui -> {
                    handler.accept(constraint);
                    return Mono.just(Boolean.TRUE);
                };
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(ObjectValue constraintFilter,
            Function<Value, Mono<Boolean>> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(Value constraint) {
                if (!(constraint instanceof ObjectValue objectConstraint)) {
                    return false;
                }
                for (var entry : constraintFilter.entrySet()) {
                    var actual = objectConstraint.get(entry.getKey());
                    if (!entry.getValue().equals(actual)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(Value constraint) {
                return ui -> handler.apply(constraint);
            }
        };
    }
}
