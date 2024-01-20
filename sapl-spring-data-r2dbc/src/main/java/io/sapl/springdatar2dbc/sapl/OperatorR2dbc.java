/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatar2dbc.sapl;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.repository.query.parser.Part;

import lombok.Getter;

@Getter
public enum OperatorR2dbc {
    BETWEEN(true, List.of("BETWEEN")), LESS_THAN(false, List.of("<")), LESS_THAN_EQUAL(false, List.of("<=")),
    GREATER_THAN(false, List.of(">")), GREATER_THAN_EQUAL(false, List.of(">=")), BEFORE(false, List.of("<")),
    AFTER(false, List.of(">")), NOT_LIKE(false, List.of("NOT LIKE")), LIKE(false, List.of("LIKE")),
    NOT_IN(true, List.of("NIN")), IN(true, List.of("IN")), REGEX(false, List.of("LIKE")),
    EXISTS(false, List.of("EXISTS")), NEGATING_SIMPLE_PROPERTY(false, List.of("<>", "!=")),
    SIMPLE_PROPERTY(false, List.of("="));

    /**
     * Creates a new {@link Part.Type} using the given keyword, number of arguments
     * to be bound and operator. Keyword and operator can be {@literal null}.
     *
     * @param sqlQueryBasedKeywords are the keywords for relational databases that
     *                              correspond to the corresponding
     *                              {@link Part.Type}.
     */
    OperatorR2dbc(boolean isArray, List<String> sqlQueryBasedKeywords) {
        this.isArray               = isArray;
        this.sqlQueryBasedKeywords = Collections.unmodifiableList(sqlQueryBasedKeywords);
    }

    public static OperatorR2dbc getOperatorByKeyword(String keyword) {
        var replacedAllSpaceKeyword = keyword.toLowerCase().replaceAll("\\s", "");
        for (OperatorR2dbc operator : OperatorR2dbc.values()) {
            var sqlQueryBasedKeywordsContainsSearchedKeyword = operator.sqlQueryBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);

            if (sqlQueryBasedKeywordsContainsSearchedKeyword) {
                return operator;
            }
        }
        throw new NotImplementedException();
    }

    final boolean      isArray;
    final List<String> sqlQueryBasedKeywords;
}
