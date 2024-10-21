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
package io.sapl.grammar.sapl.impl.util;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.SaplError;

class OperatorUtilTests {

    @Test
    void requireBoolean() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireBoolean(null, Val.TRUE)).isEqualTo(Val.TRUE);
        final var value = OperatorUtil.requireBoolean(null, Val.UNDEFINED);
        sa.assertThat(value.isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireJsonNode() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireJsonNode(null, Val.TRUE)).isEqualTo(Val.TRUE);
        sa.assertThat(OperatorUtil.requireJsonNode(null, Val.error(SaplError.UNKNOWN_ERROR)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireJsonNode(null, Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireArrayNode() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireArrayNode(null, Val.ofEmptyArray())).isEqualTo(Val.ofEmptyArray());
        sa.assertThat(OperatorUtil.requireArrayNode(null, Val.of(1)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireArrayNode(null, Val.error(SaplError.UNKNOWN_ERROR)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireArrayNode(null, Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireObjectNode() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireObjectNode(null, Val.ofEmptyObject())).isEqualTo(Val.ofEmptyObject());
        sa.assertThat(OperatorUtil.requireObjectNode(null, Val.of(1)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireObjectNode(null, Val.error(SaplError.UNKNOWN_ERROR)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireObjectNode(null, Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireText() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireText(null, Val.of(""))).isEqualTo(Val.of(""));
        sa.assertThat(OperatorUtil.requireText(null, Val.of(1)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireText(null, Val.error(SaplError.UNKNOWN_ERROR)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireText(null, Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

    @Test
    void requireNumber() {
        final var sa = new SoftAssertions();
        sa.assertThat(OperatorUtil.requireNumber(null, Val.of(1))).isEqualTo(Val.of(1));
        sa.assertThat(OperatorUtil.requireNumber(null, Val.of(1)).isError()).isFalse();
        sa.assertThat(OperatorUtil.requireNumber(null, Val.of("")).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireNumber(null, Val.error(SaplError.UNKNOWN_ERROR)).isError()).isTrue();
        sa.assertThat(OperatorUtil.requireNumber(null, Val.UNDEFINED).isError()).isTrue();
        sa.assertAll();
    }

}
