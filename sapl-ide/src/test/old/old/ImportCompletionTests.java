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
package io.sapl.grammar.ide.contentassist.old;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests regarding the auto-completion of import statements
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
class ImportCompletionTests extends CompletionTests {

    @Test
    void testCompletion_AtTheBeginningImportStatement_ReturnsLibraries() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("clock", "clock.millis", "clock.now", "clock.ticker", "temperature",
                        "temperature.mean", "temperature.now", "temperature.predicted");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithPartialLibrary_ReturnsLibrary() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import ti";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithFullLibrary_ReturnsFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import time.";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time.*", "time.after", "time.before", "time.between");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithFullLibraryAndPartialFunction_ReturnsFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import time.b";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time.before", "time.between");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithFullLibraryAndPartialFunctionAndNewLinesInBetween_ReturnsFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import
                    time.
                    b
                    """;
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(1);
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time.before", "time.between");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithPrecedingTextAndFullLibraryAndPartialFunction_ReturnsFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import time.yesterday
                    import time.b
                    """;
            String cursor = "import time.b";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time.before", "time.between");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_WithPrecedingAndSucceedingAndFullLibraryAndPartialFunction_ReturnsFunction() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import time.yesterday
                    import time.b
                    policy "test policy" deny
                    """;
            String cursor = "import time.b";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("time.before", "time.between");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

}
