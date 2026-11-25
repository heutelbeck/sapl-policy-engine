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

import com.heutelbeck.rustregex4j.RegexLimits;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.DefaultDialectRegistry;
import com.networknt.schema.dialect.DialectRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.regex.RegularExpression;
import com.networknt.schema.regex.RegularExpressionFactory;

import lombok.val;

/**
 * A networknt {@link RegularExpressionFactory} that compiles the JSON Schema
 * {@code pattern} keywords with {@link LinearRegex}, so schema patterns are
 * matched in linear time with the same syntax and limits as the operator
 * {@code =~}.
 * <p>
 * As the JSON Schema specification demands, a {@code pattern} matches if it
 * matches any part of the text. The syntax is that of {@link LinearRegex}, not
 * ECMAScript: long form Unicode properties such as {@code \p{Letter}} are read
 * as they are, and {@code $} outside multi line mode matches at the end of the
 * text only, as in ECMAScript. An invalid or too large pattern throws a
 * {@link RegexRefusedException} when the schema is loaded. A text the engine
 * cannot search, because it exceeds the text limit or holds an unpaired
 * surrogate, fails the validation with a {@link SchemaException}, so that a
 * negated pattern cannot let it through.
 */
public final class LinearRegularExpressionFactory implements RegularExpressionFactory {

    private final RegexLimits limits;

    LinearRegularExpressionFactory(RegexLimits limits) {
        this.limits = limits;
    }

    @Override
    public RegularExpression getRegularExpression(String regex) {
        val pattern = LinearRegex.compileOrRefuse(regex, limits);
        return value -> {
            try {
                return LinearRegex.matches(pattern, value);
            } catch (RegexSearchFailedException failure) {
                // networknt passes a SchemaException on without logging it.
                throw new SchemaException(failure);
            }
        };
    }

    /**
     * Installs this factory on a {@link SchemaRegistry} builder, so that every
     * {@code pattern} keyword evaluated by the resulting registry is matched in
     * linear time, and a pattern {@link LinearRegex} refuses fails the schema
     * with a {@link com.networknt.schema.SchemaException} without being logged.
     *
     * @param builder the schema-registry builder to configure
     * @param limits the limits for the patterns of the schemas
     */
    public static void applyTo(SchemaRegistry.Builder builder, RegexLimits limits) {
        applyTo(builder, limits, new DefaultDialectRegistry());
    }

    static void applyTo(SchemaRegistry.Builder builder, RegexLimits limits, DialectRegistry dialects) {
        val factory = new LinearRegularExpressionFactory(limits);
        builder.schemaRegistryConfig(SchemaRegistryConfig.builder().regularExpressionFactory(factory).build())
                .dialectRegistry(new LinearRegexDialectRegistry(factory, dialects));
    }
}
