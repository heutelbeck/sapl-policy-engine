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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.node.boot.SaplStartupConfigurationException;
import lombok.Data;
import lombok.val;

/**
 * Configuration properties for SAPL Node authentication and user management.
 * <p>
 * Every key under {@code io.sapl.node} is declared here, so that an unknown
 * key, for example a misspelt one, and a value that does not fit its key
 * refuse the start instead of going unnoticed. The keys of the transports and
 * limits are read where they are used, with their defaults there; they are
 * declared here so that the binder knows and checks them.
 */
@Data
@ConfigurationProperties(prefix = "io.sapl.node", ignoreUnknownFields = false)
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

    private static final Pattern ARGON2_PHC = Pattern.compile(
            "\\$argon2(?:id|i|d)(?:\\$v=\\d+)?\\$[a-z]=\\d+(?:,[a-z]=\\d+)*\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+");

    private static final String ERROR_NOT_ARGON2_HASH  = "SAPL Node refused to start. User '%s' has a secret or api-key that is not an Argon2 hash ($argon2id$v=19$...).";
    private static final String ACTION_NOT_ARGON2_HASH = """
            Configure the encoded value from the generator, not the plaintext,
            and without a {argon2id} prefix:

              sapl generate basic --id <user-id>
              sapl generate apikey --id <user-id>""";

    private static final String ERROR_MISSING_PDP_ID  = "SAPL Node refused to start. User '%s' has no pdp-id configured and reject-on-missing-pdp-id is enabled.";
    private static final String ACTION_MISSING_PDP_ID = """
            Either set a pdp-id on the user entry:

              io.sapl.node.users[N].pdp-id: <tenant-id>

            or relax the requirement so missing values fall back to the
            global default:

              io.sapl.node.reject-on-missing-pdp-id=false

            See the multi-tenant configuration reference at
            https://sapl.io/docs/latest/7_2_Configuration for details.""";

    // Plaintext without TLS, read by the transports and the warnings through
    // OptIn, which knows the source of the opt-in.
    private boolean allowNoTls = false;

    // The PEM certificate of both transports, mapped to an SSL bundle by
    // TlsKeysEnvironmentPostProcessor.
    private TlsKeys tls = new TlsKeys();

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

    // Read by the transports, see the class comment.
    private @Nullable Long     keepAlive;
    private @Nullable Integer  maxMultiSubscriptionCount;
    private HttpKeys           http           = new HttpKeys();
    private LimitsKeys         limits         = new LimitsKeys();
    private ScalarKeys         scalar         = new ScalarKeys();
    private OpenIdAuthzApiKeys openidAuthzApi = new OpenIdAuthzApiKeys();

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
     * Final boot-time validation: enforces apiKey constraints, refuses
     * credentials that are not Argon2 hashes, normalises or rejects missing
     * pdpId per {@link #rejectOnMissingPdpId}, and builds the api-key-id O(1)
     * lookup index.
     */
    @Override
    public void afterPropertiesSet() {
        val nextIndex = new HashMap<String, UserEntry>();
        for (UserEntry user : users) {
            if (user.getApiKey() != null) {
                requireApiKeyId(user);
                requireArgon2Hash(user, user.getApiKey());
            }
            if (user.getBasic() != null) {
                requireArgon2Hash(user, user.getBasic().getSecret());
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

    // The matcher is Spring's Argon2PasswordEncoder, which reads only the bare
    // PHC string $argon2id$v=19$m=...,t=...,p=...$salt$hash. Anything else, the
    // plaintext from the generator or a hash with the {argon2id} prefix of a
    // delegating encoder, could never authenticate.
    private static void requireArgon2Hash(UserEntry user, String credential) {
        if (credential == null || !ARGON2_PHC.matcher(credential).matches()) {
            throw new SaplStartupConfigurationException(ERROR_NOT_ARGON2_HASH.formatted(user.getId()),
                    ACTION_NOT_ARGON2_HASH);
        }
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
        // Secure by default: the keys of an http issuer would be fetched in
        // cleartext, so only an https issuer is accepted without this opt-in.
        private boolean allowInsecureIssuer = false;
    }

    /**
     * The keys of the TLS certificate that both transports serve unless one of
     * them names an SSL bundle of its own.
     */
    @Data
    public static class TlsKeys {
        private @Nullable String certificateChain;
        private @Nullable String privateKey;
    }

    /**
     * The keys of the HTTP transport.
     */
    @Data
    public static class HttpKeys {
        private @Nullable Long maxRequestBodyBytes;
        private SseKeys        sse       = new SseKeys();
        private AuthCacheKeys  authCache = new AuthCacheKeys();
    }

    /**
     * The keys of the server-sent events of the HTTP transport.
     */
    @Data
    public static class SseKeys {
        private @Nullable Integer keepAlivePoolSize;
    }

    /**
     * The keys of the authentication cache, which serves both transports.
     */
    @Data
    public static class AuthCacheKeys {
        private @Nullable Duration positiveTtl;
        private @Nullable Duration negativeTtl;
        private @Nullable Long     maxSize;
    }

    /**
     * The keys of the rate and stream limits of both transports.
     */
    @Data
    public static class LimitsKeys {
        private HttpLimitsKeys    http    = new HttpLimitsKeys();
        private RSocketLimitsKeys rsocket = new RSocketLimitsKeys();
    }

    /**
     * The keys of the limits of the HTTP transport.
     */
    @Data
    public static class HttpLimitsKeys {
        private @Nullable Integer requestsPerSecond;
        private SseLimitsKeys     sse = new SseLimitsKeys();
    }

    /**
     * The keys of the limits of the server-sent events of the HTTP transport.
     */
    @Data
    public static class SseLimitsKeys {
        private @Nullable Integer maxConcurrentStreams;
    }

    /**
     * The keys of the limits of the RSocket transport.
     */
    @Data
    public static class RSocketLimitsKeys {
        private @Nullable Integer maxConnections;
        private @Nullable Integer maxStreamsPerConnection;
        private @Nullable Integer requestsPerSecond;
    }

    /**
     * The keys of the API browser.
     */
    @Data
    public static class ScalarKeys {
        private @Nullable String oauthClientId;
        private @Nullable String oauthRedirectUri;
    }

    /**
     * The keys of the OpenID AuthZEN API.
     */
    @Data
    public static class OpenIdAuthzApiKeys {
        private @Nullable Boolean enabled;
    }

}
