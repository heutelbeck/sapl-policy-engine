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
package io.sapl.grammar.sapl.impl.util;

import io.sapl.testutil.ParserUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorFactoryTests {

    @Test
    void withCauseReturnsCauseMessage() throws IOException {
        final var errorSource = ParserUtil.expression("null");
        final var t           = new RuntimeException("A", new RuntimeException("B"));
        assertThat(ErrorFactory.causeOrMessage(errorSource, t)).isEqualTo(ErrorFactory.error(errorSource, "B"));
    }

    @Test
    void withoutCauseReturnsMessage() throws IOException {
        final var errorSource = ParserUtil.expression("null");
        final var t           = new RuntimeException("A");
        assertThat(ErrorFactory.causeOrMessage(errorSource, t)).isEqualTo(ErrorFactory.error(errorSource, "A"));
    }
}
