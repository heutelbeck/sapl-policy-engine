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
package io.sapl.node.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SaplUser")
class SaplUserTests {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates user with given id and pdpId")
        void whenValidIdAndPdpId_thenCreatesUser() {
            var user = new SaplUser("user-1", "production");

            assertThat(user.id()).isEqualTo("user-1");
            assertThat(user.pdpId()).isEqualTo("production");
        }

        @ParameterizedTest(name = "throws for null/blank id: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = { "  ", "\t", "\n" })
        @DisplayName("throws IllegalArgumentException for null or blank id")
        void whenNullOrBlankId_thenThrows(String id) {
            assertThatThrownBy(() -> new SaplUser(id, "pdp")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id must not be null or blank");
        }

        @ParameterizedTest(name = "defaults pdpId to \"default\" for: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = { "  ", "\t", "\n" })
        @DisplayName("defaults pdpId to 'default' when null or blank")
        void whenNullOrBlankPdpId_thenDefaultsToDefault(String pdpId) {
            var user = new SaplUser("user-1", pdpId);

            assertThat(user.pdpId()).isEqualTo("default");
        }

    }

    @Nested
    @DisplayName("withDefaultPdpId")
    class WithDefaultPdpIdTests {

        @Test
        @DisplayName("creates user with 'default' pdpId")
        void whenCalled_thenCreatesUserWithDefaultPdpId() {
            var user = SaplUser.withDefaultPdpId("user-1");

            assertThat(user.id()).isEqualTo("user-1");
            assertThat(user.pdpId()).isEqualTo("default");
        }

    }

}
