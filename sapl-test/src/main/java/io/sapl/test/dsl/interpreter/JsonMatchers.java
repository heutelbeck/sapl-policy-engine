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
package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom JSON matchers for Hamcrest, replacing the spotify hamcrest-jackson
 * library.
 * Provides matchers for JsonNode types without external dependencies.
 */
@UtilityClass
class JsonMatchers {

    /**
     * Matches a JSON null node.
     */
    static Matcher<JsonNode> jsonNull() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isNull();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON null");
            }
        };
    }

    /**
     * Matches any JSON text node.
     */
    static Matcher<JsonNode> jsonText() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isTextual();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON text");
            }
        };
    }

    /**
     * Matches a JSON text node with the specified value.
     */
    static Matcher<JsonNode> jsonText(String expected) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isTextual() && expected.equals(item.asText());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON text ").appendValue(expected);
            }
        };
    }

    /**
     * Matches a JSON text node where the text matches the given matcher.
     */
    static Matcher<JsonNode> jsonText(Matcher<? super String> matcher) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isTextual() && matcher.matches(item.asText());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON text matching ").appendDescriptionOf(matcher);
            }
        };
    }

    /**
     * Matches any JSON number node.
     */
    static Matcher<JsonNode> jsonNumber() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isNumber();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON number");
            }
        };
    }

    /**
     * Matches a JSON number node with the specified BigDecimal value.
     */
    static Matcher<JsonNode> jsonBigDecimal(BigDecimal expected) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                if (item == null || !item.isNumber()) {
                    return false;
                }
                return expected.compareTo(item.decimalValue()) == 0;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON number ").appendValue(expected);
            }
        };
    }

    /**
     * Matches any JSON boolean node.
     */
    static Matcher<JsonNode> jsonBoolean() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isBoolean();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON boolean");
            }
        };
    }

    /**
     * Matches a JSON boolean node with the specified value.
     */
    static Matcher<JsonNode> jsonBoolean(boolean expected) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isBoolean() && item.asBoolean() == expected;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON boolean ").appendValue(expected);
            }
        };
    }

    /**
     * Matches any JSON array node.
     */
    static Matcher<JsonNode> jsonArray() {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                return item != null && item.isArray();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON array");
            }
        };
    }

    /**
     * Matches a JSON array node where elements match the given collection matcher.
     */
    static Matcher<JsonNode> jsonArray(Matcher<? super Collection<? extends JsonNode>> elementsMatcher) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(JsonNode item) {
                if (item == null || !item.isArray()) {
                    return false;
                }
                var elements = new java.util.ArrayList<JsonNode>();
                item.forEach(elements::add);
                return elementsMatcher.matches(elements);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a JSON array matching ").appendDescriptionOf(elementsMatcher);
            }
        };
    }

    /**
     * Creates a JSON object matcher.
     */
    static IsJsonObject jsonObject() {
        return new IsJsonObject();
    }

    /**
     * A matcher for JSON objects that supports fluent "where" conditions.
     */
    static class IsJsonObject extends TypeSafeMatcher<JsonNode> {
        private final Map<String, Matcher<? super JsonNode>> fieldMatchers = new HashMap<>();

        @Override
        protected boolean matchesSafely(JsonNode item) {
            if (item == null || !item.isObject()) {
                return false;
            }
            for (var entry : fieldMatchers.entrySet()) {
                var fieldValue = item.get(entry.getKey());
                if (!entry.getValue().matches(fieldValue)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a JSON object");
            if (!fieldMatchers.isEmpty()) {
                description.appendText(" where ");
                var first = true;
                for (var entry : fieldMatchers.entrySet()) {
                    if (!first) {
                        description.appendText(" and ");
                    }
                    description.appendText(entry.getKey()).appendText(" is ").appendDescriptionOf(entry.getValue());
                    first = false;
                }
            }
        }

        /**
         * Adds a field matcher condition.
         */
        public IsJsonObject where(String key, Matcher<? super JsonNode> valueMatcher) {
            var result = new IsJsonObject();
            result.fieldMatchers.putAll(this.fieldMatchers);
            result.fieldMatchers.put(key, valueMatcher);
            return result;
        }
    }
}
