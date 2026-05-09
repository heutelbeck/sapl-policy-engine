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

import java.util.Collection;

/**
 * Provider interface for function library instances.
 * <p>
 * Use this interface to register function libraries supplied by an
 * extension module. Each entry is a fully constructed instance whose
 * class carries the {@link FunctionLibrary} annotation; the function
 * broker reads its annotated methods reflectively.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @Bean
 * FunctionLibraryProvider additionalLibraries() {
 *     return () -> List.of(new GeographicFunctionLibrary(), new MathFunctionLibrary());
 * }
 * }</pre>
 */
@FunctionalInterface
public interface FunctionLibraryProvider {

    /**
     * Returns the collection of function library instances to register.
     *
     * @return collection of function library instances
     */
    Collection<Object> functionLibraries();

}
