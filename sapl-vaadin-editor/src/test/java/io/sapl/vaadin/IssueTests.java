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
package io.sapl.vaadin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("Issue JSON parsing")
class IssueTests {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    @Nested
    @DisplayName("when the client sends well formed fields")
    class WellFormed {

        @Test
        @DisplayName("then every field is parsed from its node")
        void whenAllFieldsWellFormedThenParsed() {
            final ObjectNode json = FACTORY.objectNode();
            json.put("description", "missing semicolon");
            json.put("severity", "ERROR");
            json.put("line", 3);
            json.put("column", 7);
            json.put("offset", 42);
            json.put("length", 5);

            final Issue issue = new Issue(json);

            assertThat(issue).satisfies(parsed -> {
                assertThat(parsed.getDescription()).isEqualTo("missing semicolon");
                assertThat(parsed.getSeverity()).isEqualTo(IssueSeverity.ERROR);
                assertThat(parsed.getLine()).isEqualTo(3);
                assertThat(parsed.getColumn()).isEqualTo(7);
                assertThat(parsed.getOffset()).isEqualTo(42);
                assertThat(parsed.getLength()).isEqualTo(5);
            });
        }

        @Test
        @DisplayName("then startLine and startColumn aliases are honoured")
        void whenStartAliasesPresentThenUsedForLineAndColumn() {
            final ObjectNode json = FACTORY.objectNode();
            json.put("startLine", 11);
            json.put("startColumn", 22);

            final Issue issue = new Issue(json);

            assertThat(issue).satisfies(parsed -> {
                assertThat(parsed.getLine()).isEqualTo(11);
                assertThat(parsed.getColumn()).isEqualTo(22);
            });
        }
    }

    @Nested
    @DisplayName("when the browser client sends hostile or malformed fields")
    class Malformed {

        @Test
        @DisplayName("then construction never throws and bad fields fall back to defaults")
        void whenFieldsHaveWrongNodeTypesThenDoesNotThrow() {
            final ObjectNode json = FACTORY.objectNode();
            json.set("description", FACTORY.objectNode());
            json.set("severity", FACTORY.arrayNode());
            json.put("line", "abc");
            json.set("column", FACTORY.objectNode());
            json.set("offset", FACTORY.arrayNode());
            json.put("length", true);

            assertThatCode(() -> new Issue(json)).doesNotThrowAnyException();

            final Issue issue = new Issue(json);
            assertThat(issue).satisfies(parsed -> {
                assertThat(parsed.getDescription()).isNull();
                assertThat(parsed.getSeverity()).isEqualTo(IssueSeverity.INFO);
                assertThat(parsed.getLine()).isZero();
                assertThat(parsed.getColumn()).isZero();
                assertThat(parsed.getOffset()).isZero();
                assertThat(parsed.getLength()).isZero();
            });
        }

        @Test
        @DisplayName("then a numeric string position is still coerced to its number")
        void whenPositionIsNumericStringThenCoerced() {
            final ObjectNode json = FACTORY.objectNode();
            json.put("line", "42");

            final Issue issue = new Issue(json);

            assertThat(issue.getLine()).isEqualTo(42);
        }
    }
}
