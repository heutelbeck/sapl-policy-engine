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
package io.sapl.functions;

import io.sapl.functions.libraries.*;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides the default set of function libraries available in the SAPL
 * compiler. The instances are constructed on the first call to
 * {@link #defaults()} and cached. Callers that never invoke
 * {@code defaults()} pay no allocation cost.
 */
@UtilityClass
public class DefaultLibraries {

    private static final AtomicReference<List<Object>> CACHE = new AtomicReference<>();

    /**
     * Returns the cached list of default library instances, building
     * it on first call.
     */
    public static List<Object> defaults() {
        var current = CACHE.get();
        if (current != null) {
            return current;
        }
        var fresh = build();
        return CACHE.compareAndSet(null, fresh) ? fresh : CACHE.get();
    }

    private static List<Object> build() {
        return List.of(new ArrayFunctionLibrary(), new BitwiseFunctionLibrary(), new CidrFunctionLibrary(),
                new CsvFunctionLibrary(), new DigestFunctionLibrary(), new EncodingFunctionLibrary(),
                new FilterFunctionLibrary(), new GraphFunctionLibrary(), new GraphQLFunctionLibrary(),
                new JsonFunctionLibrary(), new KeysFunctionLibrary(), new MacFunctionLibrary(),
                new MathFunctionLibrary(), new NumeralFunctionLibrary(), new ObjectFunctionLibrary(),
                new PatternsFunctionLibrary(), new PermissionsFunctionLibrary(), new ReflectionFunctionLibrary(),
                new SanitizationFunctionLibrary(), new SaplFunctionLibrary(), new SchemaValidationLibrary(),
                new SemVerFunctionLibrary(), new SignatureFunctionLibrary(), new StandardFunctionLibrary(),
                new StringFunctionLibrary(), new TemporalFunctionLibrary(), new TomlFunctionLibrary(),
                new UnitsFunctionLibrary(), new UuidFunctionLibrary(), new X509FunctionLibrary(),
                new XmlFunctionLibrary(), new YamlFunctionLibrary());
    }
}
