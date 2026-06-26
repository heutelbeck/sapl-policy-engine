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
package io.sapl.functions.libraries;

import tools.jackson.core.StreamReadConstraints;

/**
 * Shared parse-boundary limits for the text-format function libraries (JSON,
 * YAML, CSV, TOML, XML). A function argument can carry attacker-influenced data
 * from a less-trusted source such as a Policy Information Point response, so
 * the
 * eval thread must not be exposed to an unbounded parse. The input length cap
 * bounds total work and memory, and the explicit {@link StreamReadConstraints}
 * pin nesting depth and number length independent of shifting third-party
 * defaults.
 */
final class TextParseLimits {

    static final int    MAX_INPUT_CHARS       = 1024 * 1024;
    static final String ERROR_INPUT_TOO_LARGE = "Input exceeds the maximum length of %d characters.";

    static final StreamReadConstraints STREAM_READ_CONSTRAINTS = StreamReadConstraints.builder().maxNestingDepth(500)
            .maxNumberLength(1000).maxStringLength(MAX_INPUT_CHARS).build();

    private TextParseLimits() {
    }

    static boolean exceedsMaxInput(String text) {
        return text.length() > MAX_INPUT_CHARS;
    }
}
