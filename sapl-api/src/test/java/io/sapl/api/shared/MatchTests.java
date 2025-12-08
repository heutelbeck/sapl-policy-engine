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
package io.sapl.api.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTests {

    @Test
    void noMatch_isNotBetterThanAnything() {
        assertThat(Match.NO_MATCH.isBetterThan(Match.NO_MATCH)).isFalse();
        assertThat(Match.NO_MATCH.isBetterThan(Match.EXACT_MATCH)).isFalse();
        assertThat(Match.NO_MATCH.isBetterThan(Match.VARARGS_MATCH)).isFalse();
        assertThat(Match.NO_MATCH.isBetterThan(Match.CATCH_ALL_MATCH)).isFalse();
    }

    @Test
    void exactMatch_isBetterThanAllExceptItself() {
        assertThat(Match.EXACT_MATCH.isBetterThan(Match.NO_MATCH)).isTrue();
        assertThat(Match.EXACT_MATCH.isBetterThan(Match.EXACT_MATCH)).isFalse();
        assertThat(Match.EXACT_MATCH.isBetterThan(Match.VARARGS_MATCH)).isTrue();
        assertThat(Match.EXACT_MATCH.isBetterThan(Match.CATCH_ALL_MATCH)).isTrue();
    }

    @Test
    void varargsMatch_isBetterThanNoMatchAndCatchAll() {
        assertThat(Match.VARARGS_MATCH.isBetterThan(Match.NO_MATCH)).isTrue();
        assertThat(Match.VARARGS_MATCH.isBetterThan(Match.EXACT_MATCH)).isFalse();
        assertThat(Match.VARARGS_MATCH.isBetterThan(Match.VARARGS_MATCH)).isFalse();
        assertThat(Match.VARARGS_MATCH.isBetterThan(Match.CATCH_ALL_MATCH)).isTrue();
    }

    @Test
    void catchAllMatch_isBetterThanOnlyNoMatch() {
        assertThat(Match.CATCH_ALL_MATCH.isBetterThan(Match.NO_MATCH)).isTrue();
        assertThat(Match.CATCH_ALL_MATCH.isBetterThan(Match.EXACT_MATCH)).isFalse();
        assertThat(Match.CATCH_ALL_MATCH.isBetterThan(Match.VARARGS_MATCH)).isFalse();
        assertThat(Match.CATCH_ALL_MATCH.isBetterThan(Match.CATCH_ALL_MATCH)).isFalse();
    }
}
