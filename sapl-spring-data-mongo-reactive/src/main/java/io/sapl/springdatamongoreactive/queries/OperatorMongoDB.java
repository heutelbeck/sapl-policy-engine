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
package io.sapl.springdatamongoreactive.queries;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import lombok.Getter;

/**
 * This enum class is used to use a uniform language when it comes to creating
 * queries and converting conditions from {@link io.sapl.api.pdp.Decision}s to
 * {@link SaplCondition}s.
 */
@Getter
public enum OperatorMongoDB {

    LESS_THAN(List.of("IsLessThan", "LessThan"), List.of("$lt", "lt")),
    LESS_THAN_EQUAL(List.of("IsLessThanEqual", "LessThanEqual"), List.of("$lte", "lte")),
    GREATER_THAN(List.of("IsGreaterThan", "GreaterThan"), List.of("$gt", "gt")),
    GREATER_THAN_EQUAL(List.of("IsGreaterThanEqual", "GreaterThanEqual"), List.of("$gte", "gte")),
    BEFORE(List.of("IsBefore", "Before"), List.of("$lt", "lt")),
    AFTER(List.of("IsAfter", "After"), List.of("$gt", "gt")),
    NOT_IN(List.of("IsNotIn", "NotIn"), List.of("$nin", "nin")), IN(List.of("IsIn", "In"), List.of("$in", "in")),
    NEAR(List.of("IsNear", "Near"), List.of("$near", "near")),
    REGEX(List.of("MatchesRegex", "Matches", "Regex", "IsStartingWith", "StartingWith", "StartsWith", "IsEndingWith",
            "EndingWith", "EndsWith", "IsLike", "Like", "IsContaining", "Containing", "Contains"),
            List.of("$regex", "regex")),
    EXISTS(List.of("Exists"), List.of("$exists", "exists")),
    NEGATING_SIMPLE_PROPERTY(List.of("IsNot", "Not"), List.of("$ne", "ne")),
    SIMPLE_PROPERTY(List.of("Is", "Equals"), List.of("$eq", "eq")), SORT(List.of(), List.of());

    final List<String> methodNameBasedKeywords;
    final List<String> mongoBasedKeywords;

    OperatorMongoDB(List<String> methodNameBasedKeywords, List<String> mongoBasedKeywords) {
        this.methodNameBasedKeywords = Collections.unmodifiableList(methodNameBasedKeywords);
        this.mongoBasedKeywords      = Collections.unmodifiableList(mongoBasedKeywords);
    }

    /**
     * Searches the entire enum for a keyword.
     *
     * @param keyword the keyword to search for.
     * @return the found {@link OperatorMongoDB} that contains the keyword.
     */
    public static OperatorMongoDB getOperatorByKeyword(String keyword) {

        final var replacedAllSpaceKeyword = keyword.toLowerCase().replaceAll("\\s", "");
        for (OperatorMongoDB operator : OperatorMongoDB.values()) {
            final var methodNameBasedKeywordsContainsSearchedKeyword = operator.methodNameBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);
            final var mongoBasedKeywordsContainsSearchedKeyword      = operator.mongoBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);

            if (methodNameBasedKeywordsContainsSearchedKeyword || mongoBasedKeywordsContainsSearchedKeyword) {
                return operator;
            }
        }
        throw new NotImplementedException();
    }

}
