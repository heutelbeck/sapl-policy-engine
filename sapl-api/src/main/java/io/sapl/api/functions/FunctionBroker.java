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
package io.sapl.api.functions;

import java.util.List;

import io.sapl.api.model.Value;

public interface FunctionBroker {

    /**
     * Loads a function library from the supplied instance. The
     * instance's class must be annotated with {@link FunctionLibrary}.
     * All methods annotated as functions are registered.
     *
     * @param libraryInstance the library instance
     */
    void load(Object libraryInstance);

    /**
     * Supplies the function broker for one compiled PDP configuration. A broker retaining
     * request results must isolate that result state here. Stateless brokers may be shared.
     * A stateful decorator must scope its own result state and retain the result of
     * its delegate's {@code forConfiguration()}, rather than reuse a shared result cache.
     *
     * @return the broker used for this configuration's lifetime
     */
    default FunctionBroker forConfiguration() {
        return this;
    }

    /**
     * Identifies an immutable container retained by the compiled configuration. This lets a broker
     * recognize a large constant argument by identity on subsequent requests.
     *
     * @param value a folded array or object
     */
    default void registerConstant(Value value) {
        // Optional for brokers without argument caching.
    }

    /**
     * Binds a function by name and argument count without evaluating its arguments or
     * body. An exact argument count takes precedence over a variadic match; equally
     * good matches are ambiguous. Argument types remain an evaluation concern.
     * <p>
     * The returned call belongs to this configuration's broker. Decorators must
     * decorate this entry as well as direct calls to {@link #evaluateFunction}.
     * This also applies to subclasses of {@code DefaultFunctionBroker}: overriding
     * only {@code evaluateFunction} does not intercept compiled, bound calls.
     * Host brokers must expose their supported names and arities here, including
     * functions not represented by {@link #getRegisteredLibraries()}.
     * The compiler retains this binding for the compiled configuration. Changes to
     * a broker's available overloads take effect on the next compilation, not on a
     * previously bound call. Binding must not invoke argument matchers or function
     * bodies. Constant folding evaluates the returned call separately.
     * A decorator can wrap a delegate's binding as follows:
     *
     * <pre>{@code
     * var bound = delegate.bindFunction(functionName, argumentCount);
     * return invocation -> {
     *     observe(invocation);
     *     return bound.evaluate(invocation);
     * };
     * }</pre>
     *
     * @param functionName the fully qualified function name
     * @param argumentCount the number of supplied arguments
     * @return the broker-owned call retained by the compiled configuration
     * @throws NoMatchingFunctionException if no declared function matches the name
     * and count; decorators may supply an explicitly declared mock in this case
     * @throws IllegalArgumentException if the name or count is invalid or the best
     * match is ambiguous
     */
    BoundFunction bindFunction(String functionName, int argumentCount);

    /**
     * Evaluates a direct invocation without a prior compiler binding.
     *
     * @param invocation the function name and supplied values
     * @return the function result or an error value
     */
    Value evaluateFunction(FunctionInvocation invocation);

    /**
     * Lists registered library classes for documentation and introspection. This list
     * is not the binding inventory of a host that provides functions another way.
     *
     * @return the registered library classes
     */
    List<Class<?>> getRegisteredLibraries();
}
