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
package io.sapl.test.lang;

import static io.sapl.compiler.StringsUtil.unquoteString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SaplTestParser tests")
class SaplTestParserTests {

    @Test
    void whenValidTestDefinition_thenParsesSuccessfully() {
        var testDefinition = """
                requirement "basic access control" {
                    scenario "permit admin access"
                        given
                            - document "policies/admin.sapl"
                        when subject "admin" attempts action "read" on resource "document"
                        expect permit;
                }
                """;

        var result = SaplTestParser.parse(testDefinition);

        assertThat(result).isNotNull();
        assertThat(result.requirement()).hasSize(1);
        assertThat(unquoteString(result.requirement().getFirst().name.getText())).isEqualTo("basic access control");
        assertThat(result.requirement().getFirst().scenario()).hasSize(1);
        assertThat(unquoteString(result.requirement().getFirst().scenario().getFirst().name.getText()))
                .isEqualTo("permit admin access");
    }

    @Test
    void whenMultipleScenarios_thenAllAreParsed() {
        var testDefinition = """
                requirement "multi-scenario test" {
                    scenario "scenario one"
                        given
                            - document "policy.sapl"
                        when subject "user" attempts action "read" on resource "data"
                        expect permit;

                    scenario "scenario two"
                        when subject "guest" attempts action "write" on resource "data"
                        expect deny;
                }
                """;

        var result = SaplTestParser.parse(testDefinition);

        assertThat(result.requirement().getFirst().scenario()).hasSize(2);
    }

    @Test
    void whenInvalidSyntax_thenThrowsParseException() {
        var invalidTestDefinition = """
                requirement "broken" {
                    scenario "missing when"
                        given
                            - document "test.sapl"
                        expect permit;
                }
                """;

        assertThatThrownBy(() -> SaplTestParser.parse(invalidTestDefinition)).isInstanceOf(SaplTestException.class)
                .hasMessageContaining("Parsing errors");
    }

    @Test
    void whenEmptyInput_thenThrowsParseException() {
        assertThatThrownBy(() -> SaplTestParser.parse("")).isInstanceOf(SaplTestException.class);
    }

    @Test
    void whenComplexSubscription_thenParsesAllParts() {
        var testDefinition = """
                requirement "complex subscription" {
                    scenario "with environment"
                        given
                            - document "test.sapl"
                        when subject {"name": "alice", "role": "admin"}
                            attempts action "read"
                            on resource {"id": 123, "type": "document"}
                            in environment {"time": "morning"}
                        expect permit;
                }
                """;

        var result       = SaplTestParser.parse(testDefinition);
        var scenario     = result.requirement().getFirst().scenario().getFirst();
        var subscription = scenario.whenStep().authorizationSubscription();

        assertThat(subscription.subject).isNotNull();
        assertThat(subscription.action).isNotNull();
        assertThat(subscription.resource).isNotNull();
        assertThat(subscription.env).isNotNull();
    }

}
