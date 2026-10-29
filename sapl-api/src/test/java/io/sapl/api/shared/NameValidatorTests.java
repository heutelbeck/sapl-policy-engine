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
package io.sapl.api.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.api.shared.NameValidator.requireValidName;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fully qualified attribute and function names")
class NameValidatorTests {

    @ParameterizedTest(name = "valid name \"{0}\" is accepted")
    @ValueSource(strings = { "pip.attribute", "a.b.c", "foo.bar.baz.qux", "digest.sha3_256", "digest.sha3_384",
            "digest.sha3_512", "graph.reachable_paths", "test_lib.attribute", "lib$.attr", "_library.$function2",
            "$.attr", "library._", "a1.b2", "a.b.c.d.e.f.g.h.i.j" })
    @DisplayName("when name is syntactically valid then no exception is thrown")
    void whenNameIsValidThenNoExceptionIsThrown(String name) {
        assertThatCode(() -> requireValidName(name)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "invalid name \"{0}\" is rejected")
    @ValueSource(strings = { "noseparator", "1foo.bar", "foo.1bar", "foo .bar", "foo. bar", " foo.bar", "foo.bar ", "",
            " ", ".bar", "foo.", "foo..bar", "foo-bar.baz", "foo.bar!", "`foo`.bar", "foo.`bar`", "f\u00f6o.bar",
            "foo.b\u200bar", "a.b.c.d.e.f.g.h.i.j.k" })
    @DisplayName("when name is syntactically invalid then IllegalArgumentException is thrown")
    void whenNameIsInvalidThenIllegalArgumentExceptionIsThrown(String name) {
        assertThatThrownBy(() -> requireValidName(name)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(name);
    }

    @Test
    @DisplayName("when name is null then documented IllegalArgumentException is thrown not NullPointerException")
    void whenNameIsNullThenIllegalArgumentExceptionIsThrown() {
        assertThatThrownBy(() -> requireValidName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
