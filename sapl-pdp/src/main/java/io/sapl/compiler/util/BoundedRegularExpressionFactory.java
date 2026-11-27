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

import java.util.regex.Pattern;

/**
 * A networknt {@link RegularExpressionFactory} that evaluates JSON Schema
 * {@code pattern} keywords under a wall-clock budget through
 * {@link BoundedRegex},
 * so a catastrophically backtracking pattern on attacker-influenced input
 * aborts
 * with a {@link BoundedRegex.RegexBudgetExceededException} instead of hanging
 * the
 * evaluation thread (a ReDoS denial of service).
 * <p>
 * It mirrors the default {@code JDKRegularExpressionFactory}: the same
 * {@link RegularExpressions#replaceDollarAnchors(String)} and
 * {@link RegularExpressions#replaceLongformCharacterProperties(String)}
 * normalization and the same {@code find()} (partial-match) semantics, so
 * schema
 * validation is unchanged for well-behaved patterns.
 */
public final class BoundedRegularExpressionFactory implements RegularExpressionFactory {

    private static final BoundedRegularExpressionFactory INSTANCE = new BoundedRegularExpressionFactory();

    private BoundedRegularExpressionFactory() {
    }

    public static BoundedRegularExpressionFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public RegularExpression getRegularExpression(String regex) {
        val pattern = Pattern.compile(
                RegularExpressions.replaceLongformCharacterProperties(RegularExpressions.replaceDollarAnchors(regex)));
        return value -> BoundedRegex.find(pattern, value);
    }

    /**
     * Installs this bounded factory on a {@link SchemaRegistry} builder, so that
     * every {@code pattern} keyword evaluated by the resulting registry runs
     * under the match budget.
     *
     * @param builder the schema-registry builder to configure
     */
    public static void applyTo(SchemaRegistry.Builder builder) {
        builder.schemaRegistryConfig(SchemaRegistryConfig.builder().regularExpressionFactory(INSTANCE).build());
    }
}
