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
package io.sapl.api.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class PolicyEvaluationExceptionTests {

    private static final String MESSAGE_STRING_1 = "MESSAGE STRING 1";
    private static final String MESSAGE_STRING_D = "MESSAGE STRING %d";

    @Test
    void defaultConstructor() {
        final var exception = new PolicyEvaluationException();
        assertThat(exception).isNotNull();
    }

    @Test
    void exceptionHoldsMessage() {
        final var exception = new PolicyEvaluationException(MESSAGE_STRING_D);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE_STRING_D);
    }

    @Test
    void exceptionHoldsFormattedMessage() {
        final var exception = new PolicyEvaluationException(MESSAGE_STRING_D, 1);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE_STRING_1);
    }

    @Test
    void exceptionHoldsFormattedMessageAndCause() {
        final var exception = new PolicyEvaluationException(new RuntimeException(), MESSAGE_STRING_D, 1);
        final var sa        = new SoftAssertions();
        sa.assertThat(exception.getMessage()).isEqualTo(MESSAGE_STRING_1);
        sa.assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
        sa.assertAll();
    }

    @Test
    void exceptionHoldsMessageAndCause() {
        final var exception = new PolicyEvaluationException(MESSAGE_STRING_D, new RuntimeException());
        final var sa        = new SoftAssertions();
        sa.assertThat(exception.getMessage()).isEqualTo(MESSAGE_STRING_D);
        sa.assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
        sa.assertAll();
    }

    @Test
    void exceptionHoldsCause() {
        final var exception = new PolicyEvaluationException(new RuntimeException());
        assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }

}
