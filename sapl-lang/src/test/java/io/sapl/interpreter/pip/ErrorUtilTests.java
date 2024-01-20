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
package io.sapl.interpreter.pip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class ErrorUtilTests {

    @Test
    void withCauseReturnsCauseMessage() {
        var t = new RuntimeException("A", new RuntimeException("B"));
        assertThat(ErrorUtil.causeOrMessage(t)).isEqualTo(Val.error("B"));
    }

    @Test
    void withoutCauseReturnsMessage() {
        var t = new RuntimeException("A");
        assertThat(ErrorUtil.causeOrMessage(t)).isEqualTo(Val.error("A"));
    }
}
