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
package io.sapl.functions;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class DefaultLibraries {
    public static final List<Class<?>> STATIC_LIBRARIES = List.of(StandardFunctionLibrary.class,
            FilterFunctionLibrary.class, LoggingFunctionLibrary.class, SchemaValidationLibrary.class,
            TemporalFunctionLibrary.class, GraphFunctionLibrary.class, ArrayFunctionLibrary.class,
            UuidFunctionLibrary.class, UnitsFunctionLibrary.class, ObjectFunctionLibrary.class,
            ReflectionFunctionLibrary.class, SaplFunctionLibrary.class, MathFunctionLibrary.class,
            PatternsFunctionLibrary.class);
}
