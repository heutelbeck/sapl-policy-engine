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

    @Test
    @DisplayName("every authentication mode defaults to disabled so the node fails closed")
    void whenConstructedThenAllAuthenticationModesDefaultToDisabled() {
        var properties = new SaplNodeProperties();

        assertThat(properties).satisfies(p -> {
            assertThat(p.isAllowNoAuth()).isFalse();
            assertThat(p.isAllowBasicAuth()).isFalse();
            assertThat(p.isAllowApiKeyAuth()).isFalse();
            assertThat(p.isAllowOauth2Auth()).isFalse();
        });
    }

    @Nested
    @DisplayName("pdpId normalization")
    class PdpIdNormalizationTests {

        @Test
        @DisplayName("normalizes missing pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdMissingThenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", null);
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("normalizes blank pdpId to defaultPdpId when rejectOnMissingPdpId is false")
        void whenRejectDisabledAndPdpIdBlankThenNormalizesToDefault() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);
            properties.setDefaultPdpId("fallback");

            var user = createUserEntry("user-1", "   ");
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("preserves pdpId when explicitly set")
        void whenPdpIdSetThenPreserved() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(false);

            var user = createUserEntry("user-1", "production");
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getUsers().getFirst().getPdpId()).isEqualTo("production");
        }

    }

    @Nested
    @DisplayName("pdpId rejection")
    class PdpIdRejectionTests {

        @Test
        @DisplayName("throws on missing pdpId when rejectOnMissingPdpId is true, regardless of setter order")
        void whenRejectEnabledAndPdpIdMissingThenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);
            properties.setUsers(List.of(createUserEntry("user-1", null)));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1").hasMessageContaining("no pdp-id configured");
        }

        @Test
        @DisplayName("throws on blank pdpId when rejectOnMissingPdpId is true")
        void whenRejectEnabledAndPdpIdBlankThenThrows() {
            var properties = new SaplNodeProperties();
            properties.setRejectOnMissingPdpId(true);
            properties.setUsers(List.of(createUserEntry("user-1", "   ")));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1");
        }

        @Test
        @DisplayName("rejection is order-independent: setUsers before setRejectOnMissingPdpId(true) still throws")
        void whenSetUsersFirstAndRejectFlippedTrueThenStillThrows() {
            var properties = new SaplNodeProperties();
            properties.setUsers(List.of(createUserEntry("user-1", null)));
            properties.setRejectOnMissingPdpId(true);

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1").hasMessageContaining("no pdp-id configured");
        }

    }

    private UserEntry createUserEntry(String id, String pdpId) {
        var entry = new UserEntry();
        entry.setId(id);
        entry.setPdpId(pdpId);
        return entry;
    }

}
