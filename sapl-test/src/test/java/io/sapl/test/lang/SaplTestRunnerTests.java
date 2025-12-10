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
package io.sapl.test.lang;

import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.model.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SaplTestRunnerTests {

    @Nested
    class ValueConversionTests {

        @Test
        void whenStringLiteral_thenConvertsToTextValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "string value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject "cultist" attempts action "invoke" on resource "ritual"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.of("cultist"));
        }

        @Test
        void whenNumberLiteral_thenConvertsToNumberValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "number value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject 42 attempts action "summon" on resource "entity"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.of(42));
        }

        @Test
        void whenBooleanLiteral_thenConvertsToBooleanValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "boolean value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject true attempts action "access" on resource "portal"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.TRUE);
        }

        @Test
        void whenNullLiteral_thenConvertsToNullValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "null value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject null attempts action "seek" on resource "void"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.NULL);
        }

        @Test
        void whenObjectLiteral_thenConvertsToObjectValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "object value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject {"name": "Nyarlathotep", "form": "crawling chaos"}
                                attempts action "manifest" on resource "earth"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isInstanceOf(io.sapl.api.model.ObjectValue.class);
            var objectValue = (io.sapl.api.model.ObjectValue) subject;
            assertThat(objectValue.get("name")).isEqualTo(Value.of("Nyarlathotep"));
            assertThat(objectValue.get("form")).isEqualTo(Value.of("crawling chaos"));
        }

        @Test
        void whenArrayLiteral_thenConvertsToArrayValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "array value"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject ["Cthulhu", "Dagon", "Hydra"]
                                attempts action "awaken" on resource "rlyeh"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isInstanceOf(io.sapl.api.model.ArrayValue.class);
            var arrayValue = (io.sapl.api.model.ArrayValue) subject;
            assertThat(arrayValue).hasSize(3);
            assertThat(arrayValue.getFirst()).isEqualTo(Value.of("Cthulhu"));
        }

        @Test
        void whenEmptyObject_thenConvertsToEmptyObjectValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "empty object"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject {} attempts action "observe" on resource "emptiness"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.EMPTY_OBJECT);
        }

        @Test
        void whenEmptyArray_thenConvertsToEmptyArrayValue() {
            var testDefinition = """
                    requirement "test" {
                        scenario "empty array"
                            given
                                - policy "policies/permit-all.sapl"
                            when subject [] attempts action "contemplate" on resource "nothingness"
                            expect permit;
                    }
                    """;

            var ast          = SaplTestParser.parse(testDefinition);
            var subscription = ast.getRequirements().getFirst().getScenarios().getFirst().getWhenStep()
                    .getAuthorizationSubscription();

            var subject = SaplTestRunner.toValue(subscription.getSubject());

            assertThat(subject).isEqualTo(Value.EMPTY_ARRAY);
        }
    }

    @Nested
    class TestResultTests {

        @Test
        void whenTestResultPassed_thenHasCorrectStatus() {
            var result = TestResult.passed("req", "scenario");

            assertThat(result.isPassed()).isTrue();
            assertThat(result.isFailed()).isFalse();
            assertThat(result.isError()).isFalse();
            assertThat(result.fullName()).isEqualTo("req > scenario");
        }

        @Test
        void whenTestResultFailed_thenHasCorrectStatus() {
            var result = TestResult.failed("req", "scenario", "assertion failed");

            assertThat(result.isPassed()).isFalse();
            assertThat(result.isFailed()).isTrue();
            assertThat(result.isError()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("assertion failed");
        }

        @Test
        void whenTestResultError_thenHasCorrectStatus() {
            var exception = new RuntimeException("something broke");
            var result    = TestResult.error("req", "scenario", exception);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.isFailed()).isFalse();
            assertThat(result.isError()).isTrue();
            assertThat(result.exception()).isSameAs(exception);
        }
    }
}
