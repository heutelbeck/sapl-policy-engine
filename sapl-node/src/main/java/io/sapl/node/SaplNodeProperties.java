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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for SAPL Node authentication and user management.
 */
@Data
@ConfigurationProperties(prefix = "io.sapl.node")
public class SaplNodeProperties {

    private static final String ERROR_MISSING_PDP_ID = "User '%s' has no pdpId configured and rejectOnMissingPdpId is enabled";
    private static final String ERROR_SHORT_API_KEY  = "Detected short API key in configuration. API key must be at least %d characters long.";

    // Authentication methods
    private boolean allowNoAuth     = false;
    private boolean allowBasicAuth  = true;
    private boolean allowApiKeyAuth = false;
    private boolean allowOauth2Auth = false;

    // Global PDP ID settings (applies to all auth methods)
    private boolean rejectOnMissingPdpId = false;
    private String  defaultPdpId         = "default";

    // User entries with unified credentials
    private List<UserEntry> users = new ArrayList<>();

    // OAuth2 configuration
    private OAuthConfig oauth = new OAuthConfig();

    /**
     * Returns an unmodifiable view of the user entries.
     *
     * @return unmodifiable list of user entries
     */
    public List<UserEntry> getUsers() {
        return Collections.unmodifiableList(users);
    }

    /**
     * Sets the user entries, validating and normalizing pdpId.
     * <p>
     * If a user has no pdpId and rejectOnMissingPdpId is enabled, startup fails.
     * Otherwise, missing pdpId is normalized to defaultPdpId.
     *
     * @param users the user entries to set
     */
    public void setUsers(List<UserEntry> users) {
        for (UserEntry user : users) {
            if (user.getApiKey() != null) {
                assertIsValidApiKey(user.getApiKey());
            }
            normalizeOrRejectPdpId(user);
        }
        this.users = new ArrayList<>(users);
    }

    /**
     * Sets the rejectOnMissingPdpId flag and re-validates existing users.
     *
     * @param rejectOnMissingPdpId true to reject users without pdpId at startup
     */
    public void setRejectOnMissingPdpId(boolean rejectOnMissingPdpId) {
        this.rejectOnMissingPdpId = rejectOnMissingPdpId;
        for (UserEntry user : users) {
            normalizeOrRejectPdpId(user);
        }
    }

    private void normalizeOrRejectPdpId(UserEntry user) {
        if (user.getPdpId() == null || user.getPdpId().isBlank()) {
            if (rejectOnMissingPdpId) {
                throw new IllegalStateException(ERROR_MISSING_PDP_ID.formatted(user.getId()));
            }
            user.setPdpId(defaultPdpId);
        }
    }

    private void assertIsValidApiKey(String key) {
        if (key.length() < SecretGenerator.MIN_API_KEY_LENGTH) {
            throw new IllegalStateException(ERROR_SHORT_API_KEY.formatted(SecretGenerator.MIN_API_KEY_LENGTH));
        }
    }

    /**
     * Represents a user entry with credentials for Basic or API Key authentication.
     */
    @Data
    public static class UserEntry {
        private String           id;
        private String           pdpId;
        private BasicCredentials basic;
        private String           apiKey;
    }

    /**
     * Basic authentication credentials (username and encoded secret).
     */
    @Data
    public static class BasicCredentials {
        private String username;
        private String secret;
    }

    /**
     * OAuth2/JWT configuration for extracting PDP ID from tokens.
     */
    @Data
    public static class OAuthConfig {
        private String pdpIdClaim = "sapl_pdp_id";
    }

}
