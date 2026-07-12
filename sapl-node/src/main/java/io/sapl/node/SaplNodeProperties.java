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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.node.boot.SaplStartupConfigurationException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Configuration properties for SAPL Node authentication and user management.
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "io.sapl.node")
public class SaplNodeProperties implements InitializingBean {

    private static final String ERROR_DUPLICATE_API_KEY_ID  = "SAPL Node refused to start. Duplicate api-key-id '%s' in user configuration.";
    private static final String ACTION_DUPLICATE_API_KEY_ID = """
            Each api-key-id under io.sapl.node.users must be unique. The
            api-key-id is the middle segment of the wire token sapl_<id>_<secret>
            and is used for O(1) routing.

            Generate fresh credentials with:

              sapl generate apikey --id <user-id>

            then replace the duplicates.""";

    private static final String ERROR_MISSING_API_KEY_ID  = "SAPL Node refused to start. User '%s' has an api-key but no api-key-id configured.";
    private static final String ACTION_MISSING_API_KEY_ID = """
            Every api-key user entry needs an api-key-id, the middle segment of
            the wire token sapl_<id>_<secret>, so the server can find the entry.

            Generate fresh credentials with:

              sapl generate apikey --id <user-id>

            then copy both the api-key-id and api-key into the user entry.""";

    private static final String ERROR_MISSING_PDP_ID  = "SAPL Node refused to start. User '%s' has no pdp-id configured and reject-on-missing-pdp-id is enabled.";
    private static final String ACTION_MISSING_PDP_ID = """
            Either set a pdp-id on the user entry:

              io.sapl.node.users[N].pdp-id: <tenant-id>

            or relax the requirement so missing values fall back to the
            global default:

              io.sapl.node.reject-on-missing-pdp-id=false

            See the multi-tenant configuration reference at
            https://sapl.io/docs/latest/7_2_Configuration for details.""";

    // Authentication methods
    private boolean allowNoAuth     = false;
    private boolean allowBasicAuth  = false;
    private boolean allowApiKeyAuth = false;
    private boolean allowOauth2Auth = false;

    // Global PDP ID settings (applies to all auth methods).
    // All boot-time validation runs in afterPropertiesSet() once Spring
    // has called every setter, so the binder's setter ordering does not
    // affect the outcome.
    private boolean rejectOnMissingPdpId = false;
    private String  defaultPdpId         = StreamingPolicyDecisionPoint.DEFAULT_PDP_ID;

    // User entries with unified credentials.
    private List<UserEntry> users = new ArrayList<>();

    // O(1) lookup index for API key authentication, rebuilt on every setUsers().
    // Indexed by the public api-key-id segment of the sapl_<id>_<secret> wire
    // format.
    private Map<String, UserEntry> apiKeyIdIndex = Map.of();

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
     * Stores the user entries. Validation, pdpId normalisation and
     * apiKeyIdIndex construction happen in {@link #afterPropertiesSet()}
     * once Spring has bound every setter, so binder ordering between
     * {@code setUsers}, {@code setRejectOnMissingPdpId} and
     * {@code setDefaultPdpId} does not affect the outcome.
     *
     * @param users the user entries to set
     */
    public void setUsers(List<UserEntry> users) {
        this.users = new ArrayList<>(users);
    }

    /**
     * Final boot-time validation: enforces apiKey constraints, normalises
     * or rejects missing pdpId per {@link #rejectOnMissingPdpId}, and
     * builds the api-key-id O(1) lookup index.
     */
    @Override
    public void afterPropertiesSet() {
        val nextIndex = new HashMap<String, UserEntry>();
        for (UserEntry user : users) {
            if (user.getApiKey() != null) {
                requireApiKeyId(user);
                warnIfApiKeyLooksPlaintext(user);
            }
            normalizeOrRejectPdpId(user);
            val apiKeyId = user.getApiKeyId();
            if (apiKeyId != null && !apiKeyId.isBlank() && nextIndex.putIfAbsent(apiKeyId, user) != null) {
                throw new SaplStartupConfigurationException(ERROR_DUPLICATE_API_KEY_ID.formatted(apiKeyId),
                        ACTION_DUPLICATE_API_KEY_ID);
            }
        }
        this.apiKeyIdIndex = Map.copyOf(nextIndex);
    }

    private void warnIfApiKeyLooksPlaintext(UserEntry user) {
        // Two formats are accepted by the matcher: Spring's delegated form
        // {algo}<hash> (e.g. {argon2id}$argon2id$v=19$...) and the bare PHC
        // string $argon2*$... produced directly by Argon2PasswordEncoder.
        // A configured apiKey matching neither shape is almost certainly
        // the plaintext from `sapl generate apikey` instead of the encoded
        // line, and the matcher will silently fail at every request.
        val key = user.getApiKey();
        if (!looksEncoded(key)) {
            log.warn("User '{}' has an apiKey that does not look encoded. "
                    + "The matcher requires the encoded form; configure the "
                    + "{{argon2id}}... or $argon2id$... value from the generator " + "output, not the plaintext key.",
                    user.getId());
        }
    }

    private static boolean looksEncoded(String key) {
        return key.startsWith("{") || key.startsWith("$argon2id$") || key.startsWith("$argon2i$")
                || key.startsWith("$argon2d$");
    }

    /**
     * Returns an O(1) lookup map from {@code api-key-id} (the public middle
     * segment of {@code sapl_<id>_<secret>}) to user entry. Built at
     * {@link #setUsers} time; users without an {@code api-key-id} configured
     * are omitted.
     *
     * @return immutable map by api-key-id. Empty when no users configured
     */
    public Map<String, UserEntry> getApiKeyIdIndex() {
        return apiKeyIdIndex;
    }

    /**
     * Stores the flag. Per-user validation runs once in
     * {@link #afterPropertiesSet()} so binder ordering does not matter.
     *
     * @param rejectOnMissingPdpId true to reject users without pdpId at startup
     */
    public void setRejectOnMissingPdpId(boolean rejectOnMissingPdpId) {
        this.rejectOnMissingPdpId = rejectOnMissingPdpId;
    }

    private void normalizeOrRejectPdpId(UserEntry user) {
        if (user.getPdpId() == null || user.getPdpId().isBlank()) {
            if (rejectOnMissingPdpId) {
                throw new SaplStartupConfigurationException(ERROR_MISSING_PDP_ID.formatted(user.getId()),
                        ACTION_MISSING_PDP_ID);
            }
            user.setPdpId(defaultPdpId);
        }
    }

    private static void requireApiKeyId(UserEntry user) {
        val apiKeyId = user.getApiKeyId();
        if (apiKeyId == null || apiKeyId.isBlank()) {
            throw new SaplStartupConfigurationException(ERROR_MISSING_API_KEY_ID.formatted(user.getId()),
                    ACTION_MISSING_API_KEY_ID);
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
        private String           apiKeyId;
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
        private String       pdpIdClaim = "sapl_pdp_id";
        private List<String> audiences  = new ArrayList<>();
        // Empty means no scope gate: any token passing signature, issuer,
        // timestamp, and audience validation is accepted, as before.
        private List<String> requiredScopes = new ArrayList<>();
        // Secure by default: a JWT without an exp claim is rejected. It would grant
        // non-expiring access.
        private boolean allowJwtWithoutExpiry = false;
    }

}
