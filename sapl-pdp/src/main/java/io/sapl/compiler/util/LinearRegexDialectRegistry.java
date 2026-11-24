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

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaContext;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.DefaultDialectRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.DialectRegistry;
import com.networknt.schema.keyword.Keyword;
import com.networknt.schema.keyword.KeywordType;
import com.networknt.schema.keyword.KeywordValidator;
import lombok.val;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the JSON Schema dialects as networknt does, with the keywords that
 * compile regular expressions changed so that a pattern {@link LinearRegex}
 * refuses fails the schema with a {@link SchemaException} that networknt
 * passes on silently.
 * <p>
 * Unchanged, networknt logs a pattern it cannot compile at ERROR, the pattern
 * text included, and logs a keyword that fails with an exception other than a
 * {@link SchemaException} at WARN. Schemas may come from authorization
 * subscriptions, so a client could flood the log and write text of its choice
 * into it. The refusal reaches the author of the schema as an error instead.
 */
final class LinearRegexDialectRegistry implements DialectRegistry {

    private static final Set<String> KEYWORDS_WITH_PATTERNS = Set.of(KeywordType.PATTERN.getValue(),
            KeywordType.PATTERN_PROPERTIES.getValue(), KeywordType.ADDITIONAL_PROPERTIES.getValue());

    private final DialectRegistry                standardDialects = new DefaultDialectRegistry();
    private final Map<Dialect, Dialect>          dialects         = new ConcurrentHashMap<>();
    private final LinearRegularExpressionFactory patterns;

    LinearRegexDialectRegistry(LinearRegularExpressionFactory patterns) {
        this.patterns = patterns;
    }

    @Override
    public Dialect getDialect(String dialectId, SchemaRegistry schemaRegistry) {
        return dialects.computeIfAbsent(standardDialects.getDialect(dialectId, schemaRegistry),
                this::refusingPatternsSilently);
    }

    private Dialect refusingPatternsSilently(Dialect dialect) {
        val keywords = dialect.getKeywords().values().stream()
                .filter(keyword -> KEYWORDS_WITH_PATTERNS.contains(keyword.getValue()))
                .map(keyword -> new SilentlyRefusingKeyword(keyword, patterns)).toList();
        return Dialect.builder(dialect).keywords(keywords).build();
    }

    /**
     * A keyword that turns the refusal of a pattern into a
     * {@link SchemaException}. The {@code pattern} keyword checks its pattern
     * before networknt compiles it, as networknt logs a refusal before passing it
     * on.
     */
    private record SilentlyRefusingKeyword(Keyword keyword, LinearRegularExpressionFactory patterns)
            implements Keyword {

        @Override
        public String getValue() {
            return keyword.getValue();
        }

        @Override
        public KeywordValidator newValidator(SchemaLocation schemaLocation, JsonNode schemaNode, Schema parentSchema,
                SchemaContext schemaContext) throws Exception {
            try {
                if (KeywordType.PATTERN.getValue().equals(getValue()) && schemaNode.isString()) {
                    patterns.getRegularExpression(schemaNode.asString());
                }
                return keyword.newValidator(schemaLocation, schemaNode, parentSchema, schemaContext);
            } catch (RegexRefusedException e) {
                throw new SchemaException(e);
            }
        }
    }
}
