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
package io.sapl.test.plain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TestValidationException tests")
class TestValidationExceptionTests {

    @Test
    @DisplayName("creates exception with message")
    void whenCreatingWithMessage_thenMessageIsSet() {
        var exception = new TestValidationException("validation failed");

        assertThat(exception).hasMessage("validation failed");
    }

    @Test
    @DisplayName("creates exception with message and cause")
    void whenCreatingWithMessageAndCause_thenBothAreSet() {
        var cause     = new IllegalArgumentException("root cause");
        var exception = new TestValidationException("validation failed", cause);

        assertThat(exception).hasMessage("validation failed").hasCause(cause);
    }

    @Test
    @DisplayName("exception is a RuntimeException")
    void whenCreatingException_thenIsRuntimeException() {
        var exception = new TestValidationException("test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
