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
package io.sapl.compiler.util;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;
import com.networknt.schema.regex.RegularExpressions;

import lombok.val;

/**
 * A networknt {@link RegularExpressionFactory} that compiles the JSON Schema
 * {@code pattern} keywords with {@link LinearRegex}, so schema patterns are
 * matched in linear time with the same syntax and limits as the operator
 * {@code =~}.
 * <p>
 * As the JSON Schema specification demands, a {@code pattern} matches if it
 * matches any part of the text. Long form Unicode properties such as
 * {@code \p{Letter}} are rewritten to their short forms, which RE2 reads. In
 * RE2, {@code $} outside multi line mode matches at the end of the text only,
 * as in ECMAScript, so it needs no rewriting. An invalid or too large pattern
 * throws a {@link com.google.re2j.PatternSyntaxException} when the schema is
 * loaded.
 */
public final class LinearRegularExpressionFactory implements RegularExpressionFactory {

    private static final LinearRegularExpressionFactory INSTANCE = new LinearRegularExpressionFactory();

    private LinearRegularExpressionFactory() {
    }

    public static LinearRegularExpressionFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public RegularExpression getRegularExpression(String regex) {
        val pattern = LinearRegex.compile(RegularExpressions.replaceLongformCharacterProperties(regex));
        return value -> pattern.matcher(value).find();
    }

    /**
     * Installs this factory on a {@link SchemaRegistry} builder, so that every
     * {@code pattern} keyword evaluated by the resulting registry is matched in
     * linear time, and a pattern RE2/J refuses fails the schema with a
     * {@link com.networknt.schema.SchemaException} without being logged.
     *
     * @param builder the schema-registry builder to configure
     */
    public static void applyTo(SchemaRegistry.Builder builder) {
        builder.schemaRegistryConfig(SchemaRegistryConfig.builder().regularExpressionFactory(INSTANCE).build())
                .dialectRegistry(new LinearRegexDialectRegistry());
    }
}
