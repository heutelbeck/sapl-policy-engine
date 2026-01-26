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

import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.BitwiseFunctionLibrary;
import io.sapl.functions.libraries.CidrFunctionLibrary;
import io.sapl.functions.libraries.CsvFunctionLibrary;
import io.sapl.functions.libraries.DigestFunctionLibrary;
import io.sapl.functions.libraries.EncodingFunctionLibrary;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.GraphFunctionLibrary;
import io.sapl.functions.libraries.GraphQLFunctionLibrary;
import io.sapl.functions.libraries.JWTFunctionLibrary;
import io.sapl.functions.libraries.JsonFunctionLibrary;
import io.sapl.functions.libraries.KeysFunctionLibrary;
import io.sapl.functions.libraries.MacFunctionLibrary;
import io.sapl.functions.libraries.MathFunctionLibrary;
import io.sapl.functions.libraries.NumeralFunctionLibrary;
import io.sapl.functions.libraries.ObjectFunctionLibrary;
import io.sapl.functions.libraries.PatternsFunctionLibrary;
import io.sapl.functions.libraries.PermissionsFunctionLibrary;
import io.sapl.functions.libraries.ReflectionFunctionLibrary;
import io.sapl.functions.libraries.SanitizationFunctionLibrary;
import io.sapl.functions.libraries.SaplFunctionLibrary;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.functions.libraries.SemVerFunctionLibrary;
import io.sapl.functions.libraries.SignatureFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.functions.libraries.TomlFunctionLibrary;
import io.sapl.functions.libraries.UnitsFunctionLibrary;
import io.sapl.functions.libraries.UuidFunctionLibrary;
import io.sapl.functions.libraries.X509FunctionLibrary;
import io.sapl.functions.libraries.XmlFunctionLibrary;
import io.sapl.functions.libraries.YamlFunctionLibrary;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Provides the default set of function libraries available in the SAPL
 * compiler. These libraries are automatically
 * registered when creating a PDP with default configuration.
 */
@UtilityClass
public class DefaultLibraries {
    // @formatter:off
    public static final List<Class<?>> STATIC_LIBRARIES = List.of(
            ArrayFunctionLibrary.class,
            BitwiseFunctionLibrary.class,
            CidrFunctionLibrary.class,
            CsvFunctionLibrary.class,
            DigestFunctionLibrary.class,
            EncodingFunctionLibrary.class,
            FilterFunctionLibrary.class,
            GraphFunctionLibrary.class,
            GraphQLFunctionLibrary.class,
            JsonFunctionLibrary.class,
            JWTFunctionLibrary.class,
            KeysFunctionLibrary.class,
            MacFunctionLibrary.class,
            MathFunctionLibrary.class,
            NumeralFunctionLibrary.class,
            ObjectFunctionLibrary.class,
            PatternsFunctionLibrary.class,
            PermissionsFunctionLibrary.class,
            ReflectionFunctionLibrary.class,
            SanitizationFunctionLibrary.class,
            SaplFunctionLibrary.class,
            SchemaValidationLibrary.class,
            SemVerFunctionLibrary.class,
            SignatureFunctionLibrary.class,
            StandardFunctionLibrary.class,
            StringFunctionLibrary.class,
            TemporalFunctionLibrary.class,
            TomlFunctionLibrary.class,
            UnitsFunctionLibrary.class,
            UuidFunctionLibrary.class,
            X509FunctionLibrary.class,
            XmlFunctionLibrary.class,
            YamlFunctionLibrary.class);
    // @formatter:on
}
