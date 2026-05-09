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

import io.sapl.api.model.Value;

import java.util.List;

public interface FunctionBroker {

    /**
     * Loads a function library from the supplied instance. The
     * instance's class must be annotated with {@link FunctionLibrary}.
     * All methods annotated as functions are registered.
     */
    void load(Object libraryInstance);

    Value evaluateFunction(FunctionInvocation invocation);

    List<Class<?>> getRegisteredLibraries();
}
