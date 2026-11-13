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
package io.sapl.attributeapi.auth;

import io.sapl.attributeapi.auth.AttributeApiSecurityProperties.UserEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeApiSecurityPropertiesTests {

    private static UserEntry userWith(String id, String apiKeyId, String apiKey) {
        var user = new UserEntry();
        user.setId(id);
        user.setApiKeyId(apiKeyId);
        user.setApiKey(apiKey);
        return user;
    }

    @Nested
    class AfterPropertiesSetTests {

        @Test
        @DisplayName("A duplicate api keys let's the server startup fail")
        void whenApiKeyIdIsDuplicateThenStartupFails() {
            var user1 = userWith("user-1", "duplicate", "$argon2id$...");
            var user2 = userWith("user-2", "duplicate", "$argon2id$...");

            var properties = new AttributeApiSecurityProperties();
            properties.setUsers(List.of(user1, user2));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dup");
        }

        @Test
        @DisplayName("A user without a valid api key will be ignored on server startup")
        void whenUserHasNoApiKeyThenUserIsIgnored() {
            var user = userWith("user-1", null, null);

            var properties = new AttributeApiSecurityProperties();
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getApiKeyIdIndex()).isEmpty();
        }

        @Test
        @DisplayName("A user with a valid key is included in the generated list of valid users on server startup")
        void whenConfigurationIsValidThenUserIsInIndexList() {
            var user = userWith("user-1", "id1", "$argon2id$...");

            var properties = new AttributeApiSecurityProperties();
            properties.setUsers(List.of(user));
            properties.afterPropertiesSet();

            assertThat(properties.getApiKeyIdIndex()).containsEntry("id1", user);
        }

        @Test
        @DisplayName("When a pdp id is too long then reject it")
        void whenPdpIdIsTooLongThenRejectIt() {
            var pdpId = "a".repeat(256);
            assertThat(AttributeApiSecurityProperties.isValidPdpId(pdpId)).isFalse();
        }

        @Test
        @DisplayName("When a pdp id is null then reject it")
        void whenPdpIdIsNullThenRejectIt() {
            assertThat(AttributeApiSecurityProperties.isValidPdpId(null)).isFalse();
        }

        @ParameterizedTest(name = "apiKeyId={0}, apiKey={1}")
        @CsvSource(nullValues = "null", value = { "null,         $argon2id$...", "' ',          $argon2id$...",
                "cleartextKey, this-is-cleartext", "''          , $argon2id$...", "id1         , $2a$10$abc",
                "id1         , argon2id$x" })
        @DisplayName("An invalid api keys throws an exception and let the startup fail")
        void whenApiKeyConfigurationIsInvalidThenStartupFails(String apiKeyId, String apiKey) {
            var properties = new AttributeApiSecurityProperties();
            properties.setUsers(List.of(userWith("user-1", apiKeyId, apiKey)));

            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("user-1");
        }
    }
}
