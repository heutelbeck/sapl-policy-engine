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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaplNodeMainTests {

    private PrintStream originalOut;

    @BeforeEach
    void suppressOutput() {
        originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
    }

    @Test
    void whenExecutingMain_withGenerateApiKeyCommand_thenSuccessful() {
        assertThatCode(() -> SaplNodeApplication.main(new String[] { "generate", "apikey" }))
                .doesNotThrowAnyException();
    }

    @Test
    void whenExecutingMain_withGenerateBasicCommand_thenSuccessful() {
        assertThatCode(() -> SaplNodeApplication.main(new String[] { "generate", "basic" })).doesNotThrowAnyException();
    }

}
