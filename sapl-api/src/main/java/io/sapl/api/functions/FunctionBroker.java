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
     */
    void load(Object libraryInstance);

    /**
     * Supplies the function broker for one compiled PDP configuration. A broker retaining
     * request results can isolate them here. Stateless and host-supplied brokers may be shared.
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

    Value evaluateFunction(FunctionInvocation invocation);

    List<Class<?>> getRegisteredLibraries();
}
