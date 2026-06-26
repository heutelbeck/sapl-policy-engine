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
package io.sapl.node.boot;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import lombok.val;

@DisplayName("startup banner")
class SaplBannerTests {

    // The colored banner emits SGR truecolor sequences (ESC[38;2;r;g;bm); the
    // distinctive "38;2;" marker is absent from the plain variant.
    private static final String TRUECOLOR_MARKER = "38;2;";

    @Test
    @DisplayName("emits no ANSI color when the output is not an interactive terminal")
    void whenNotATerminalThenBannerHasNoColor() {
        val captured = new ByteArrayOutputStream();

        new SaplBanner().printBanner(new StandardEnvironment(), SaplBannerTests.class,
                new PrintStream(captured, true, UTF_8));

        assertThat(captured.toString(UTF_8)).doesNotContain(TRUECOLOR_MARKER).contains("OS:");
    }

}
