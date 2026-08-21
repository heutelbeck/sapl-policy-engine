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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api")
public class AttributeApiSecurityProperties implements InitializingBean {
    private static final String ERROR_DUPLICATE_API_KEY_ID = "Attribute API failed to start. A duplicate api-key-id '%s' was found in the configuration.";
    private static final String ERROR_MISSING_API_KEY_ID   = "Attribute API failed to start. User '%s' has set an api-key but no api-key-id configured.";
    private static final String WARN_KEY_IS_NOT_ENCODED    = "The given api key for user '%s' does not look encoded. Please set the argon2 encoded value.";

    private boolean allowNoAuth;
    private boolean allowBasicAuth;
    private boolean allowApiKeyAuth;
    private boolean allowOAuth2Auth;

    private String defaultPdpId = "default";
    private OAuth2 oauth2       = new OAuth2();

    // Users from the given configuration
    private List<UserEntry> users = new ArrayList<>();

    // Map the user entry and the api key id to find the configured users fast
    private Map<String, UserEntry> apiKeyIdIndex = Map.of();

    // Read-only list of users
    public List<UserEntry> getUsers() {
        return Collections.unmodifiableList(users);
    }

    // Create a copy of the users list to prevent external modifications of it
    public void setUsers(List<UserEntry> users) {
        this.users = new ArrayList<>(users);
    }

    public Map<String, UserEntry> getApiKeyIdIndex() {
        return apiKeyIdIndex;
    }

    @Override
    public void afterPropertiesSet() {
        val nextIndex = new HashMap<String, UserEntry>();

        for (UserEntry user : users) {
            if (user.getApiKey() != null) {
                apiKeyCheck(user);
            }

            val apiKeyId = user.getApiKeyId();

            if (apiKeyId != null && !apiKeyId.isBlank() && nextIndex.putIfAbsent(apiKeyId, user) != null) {
                throw new IllegalStateException(ERROR_DUPLICATE_API_KEY_ID.formatted(apiKeyId));
            }
        }

        this.apiKeyIdIndex = Map.copyOf(nextIndex);
    }

    private void apiKeyCheck(UserEntry user) {
        // Check if the api key id is set for the given user
        val apiKeyId = user.getApiKeyId();
        if (apiKeyId == null || apiKeyId.isBlank()) {
            throw new IllegalStateException(ERROR_MISSING_API_KEY_ID.formatted(user.getId()));
        }

        // Check if the api key was pasted instead of the encoded hash
        val key       = user.getApiKey();
        val isEncoded = key.startsWith("{") || key.startsWith("$argon2id$") || key.startsWith("$argon2i$")
                || key.startsWith("$argon2d$");

        if (!isEncoded) {
            log.warn(WARN_KEY_IS_NOT_ENCODED.formatted(user.getId()));
        }
    }

    @Data
    public static class UserEntry {
        private String id;
        private String pdpId;
        private String username;
        private String secret;
        private String apiKeyId;
        private String apiKey;
    }

    @Data
    public static class OAuth2 {
        private String oidcPdpIdClaim = "pdp_id";
    }
}
