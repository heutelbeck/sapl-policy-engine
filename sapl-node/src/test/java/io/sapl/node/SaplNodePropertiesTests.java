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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.node.SaplNodeProperties.UserEntry;

@DisplayName("SaplNodeProperties")
class SaplNodePropertiesTests {

    @Nested
    @DisplayName("pdpId normalization")
    class PdpIdNormalizationTests {

        @Test
        @DisplayName("normalizes missing pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdMissing_thenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", null);
            properties.setUsers(List.of(user));

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("normalizes blank pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdBlank_thenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", "   ");
            properties.setUsers(List.of(user));

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("preserves pdpId when explicitly set")
        void whenPdpIdSet_thenPreserved() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);

            var user = createUserEntry("user-1", "production");
            properties.setUsers(List.of(user));

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("production");
        }

    }

    @Nested
    @DisplayName("pdpId rejection")
    class PdpIdRejectionTests {

        @Test
        @DisplayName("throws when pdpId missing and rejectOnMissingPdpId is true")
        void whenRejectEnabledAndPdpIdMissing_thenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);

            var user = createUserEntry("user-1", null);

            assertThatThrownBy(() -> properties.setUsers(List.of(user))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1").hasMessageContaining("no pdpId configured");
        }

        @Test
        @DisplayName("throws when pdpId blank and rejectOnMissingPdpId is true")
        void whenRejectEnabledAndPdpIdBlank_thenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);

            var user = createUserEntry("user-1", "   ");

            assertThatThrownBy(() -> properties.setUsers(List.of(user))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1");
        }

        @Test
        @DisplayName("already-normalized users are not rejected when rejectOnMissingPdpId changes to true")
        void whenRejectChangesToTrue_thenAlreadyNormalizedUsersAreAccepted() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", null);
            properties.setUsers(List.of(user));

            // pdpId was normalized to "fallback", so changing flag should not throw
            properties.setRejectOnMissingPdpId(true);

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

    }

    private UserEntry createUserEntry(String id, String pdpId) {
        var entry = new UserEntry();
        entry.setId(id);
        entry.setPdpId(pdpId);
        return entry;
    }

}
