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
import lombok.ToString;
import lombok.val;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings of the Attribute API under {@code io.sapl.attribute-api}. It
 * contains the auth modes, users, OAuth2 and request limits. Users are
 * validated at startup. Invalid configuration stop the application.
 */
@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api")
public class AttributeApiSecurityProperties implements InitializingBean {
    private static final String ERROR_DUPLICATE_API_KEY_ID = "Attribute API failed to start. A duplicate api-key-id '%s' was found in the configuration.";
    private static final String ERROR_MISSING_API_KEY_ID   = "Attribute API failed to start. User '%s' has set an api-key but no api-key-id configured.";
    private static final String ERROR_KEY_IS_NOT_ENCODED   = "Attribute API failed to start. The given api key for user '%s' does not look encoded. "
            + "Please set the argon2 encoded value.";
    private static final String ERROR_INVALID_PDP_ID       = "Attribute API failed to start. User '%s' has an invalid pdp-id '%s'. "
            + "Only alphanumeric characters, hyphens, underscores, and dots are allowed.";

    private static final Pattern VALID_PDP_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int     MAX_PDP_ID_LENGTH    = 255;

    private boolean allowNoAuth;
    private boolean allowBasicAuth;
    private boolean allowApiKeyAuth;
    private boolean allowOAuth2Auth;
    private String  defaultPdpId = "default";
    private int     maxArguments = 50;
    private int     maxLimit     = 1000;
    private OAuth2  oauth2       = new OAuth2();

    // Users from the given configuration
    private List<UserEntry> users = new ArrayList<>();

    // Map the user entry and the api key id to find the configured users fast
    private Map<String, UserEntry> apiKeyIdIndex = Map.of();

    /**
     * A read-only list of all configured users.
     *
     * @return the read-only list of all users.
     */
    public List<UserEntry> getUsers() {
        return Collections.unmodifiableList(users);
    }

    /**
     * Creates a copy of the users list to prevent external modification while
     * loaded.
     *
     * @param users The list of users
     */
    public void setUsers(List<UserEntry> users) {
        this.users = new ArrayList<>(users);
    }

    @Override
    public void afterPropertiesSet() {
        val nextIndex = new HashMap<String, UserEntry>();

        for (UserEntry user : users) {
            if (user.getPdpId() != null && !isValidPdpId(user.getPdpId())) {
                throw new IllegalStateException(ERROR_INVALID_PDP_ID.formatted(user.getId(), user.getPdpId()));
            }

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

    // Pre-checks for the api key
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
            throw new IllegalStateException(ERROR_KEY_IS_NOT_ENCODED.formatted(user.getId()));
        }
    }

    /**
     * Checks if the pdp id is not too long and also if the pattern is compliant.
     *
     * @param pdpId The pdp id to check for.
     * @return {@code true}, if the it's a valid pdp id.
     */
    public static boolean isValidPdpId(String pdpId) {
        return pdpId != null && pdpId.length() <= MAX_PDP_ID_LENGTH && VALID_PDP_ID_PATTERN.matcher(pdpId).matches();
    }

    /**
     * A user of the attribute api. The users are configured in standalone mode
     * under {@code io.sapl.attribute-api.users}. A user can be authenticated with
     * Basic auth, via API key, or both.
     */
    @Data
    public static class UserEntry {
        private String id;
        private String pdpId;
        private String username;
        @ToString.Exclude
        private String secret;
        private String apiKeyId;
        @ToString.Exclude
        private String apiKey;
    }

    /**
     * Specific OAuth2 settings. Can be configured under
     * {@code io.sapl.attribute-api.oauth2}.
     */
    @Data
    public static class OAuth2 {
        private String oidcPdpIdClaim = "pdp_id";
    }
}
