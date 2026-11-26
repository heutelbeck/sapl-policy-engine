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

/** A broker-owned call bound to a function name and argument count during compilation. */
@FunctionalInterface
public interface BoundFunction {

    /**
     * Evaluates one reached function call through its broker. Decorators observe each
     * call before matching mocks or delegating to a cached function result.
     * Evaluation failures are returned as error values rather than thrown.
     *
     * @param invocation the invocation with the bound name and argument count
     * @return the result, including an error value for an evaluation failure
     */
    Value evaluate(FunctionInvocation invocation);
}
