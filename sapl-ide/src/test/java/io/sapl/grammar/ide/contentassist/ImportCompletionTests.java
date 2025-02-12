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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

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
        final var document = "import §";
        final var expected = List.of("clock", "clock.millis", "clock.now", "clock.ticker", "temperature",
                "temperature.mean", "temperature.now", "temperature.predicted");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_WithPartialLibrary_ReturnsLibrary() {
        final var document = "import ti§";
        final var expected = List.of("time");
        final var unwanted = List.of("time.after(t1, t2)");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_WithFullLibrary_ReturnsFunction() {
        final var document = "import time.§";
        final var expected = List.of("*", "after", "before", "between");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_WithFullLibraryAndPartialFunction_ReturnsFunction() {
        final var document = "import time.b§";
        final var expected = List.of("before", "between");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_WithPrecedingTextAndFullLibraryAndPartialFunction_ReturnsFunction() {
        final var document = """
                import time.yesterday
                import time.b§
                """;
        final var expected = List.of("before", "between");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_WithPrecedingAndSucceedingAndFullLibraryAndPartialFunction_ReturnsFunction() {
        final var document = """
                import time.yesterday
                import time.b§
                policy "test policy" deny
                """;
        final var expected = List.of("before", "between");
        assertProposalsContain(document, expected);
    }

}
