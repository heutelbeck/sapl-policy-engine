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
package io.sapl.compiler;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanOperatorsTests {

    // ========== NOT ==========

    @Test
    void when_not_withTrue_then_returnsFalse() {
        val actual = BooleanOperators.not(null, Value.TRUE);
        assertThat(actual).isEqualTo(Value.FALSE);
    }

    @Test
    void when_not_withFalse_then_returnsTrue() {
        val actual = BooleanOperators.not(null, Value.FALSE);
        assertThat(actual).isEqualTo(Value.TRUE);
    }

    @Test
    void when_not_withNonBoolean_then_returnsError() {
        val actual = BooleanOperators.not(null, Value.of(5));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Logical operation requires boolean value");
    }

    @Test
    void when_not_withString_then_returnsError() {
        val actual = BooleanOperators.not(null, Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_not_withNull_then_returnsError() {
        val actual = BooleanOperators.not(null, Value.NULL);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_not_withError_then_returnsError() {
        val error  = Value.error("original error");
        val actual = BooleanOperators.not(null, error);
        assertThat(actual).isSameAs(error);
    }

}
