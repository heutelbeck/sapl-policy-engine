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
package io.sapl.pdp.configuration.source;

import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for a remote bundle PDP configuration source.
 * <p>
 * Bundles are fetched from {@code {baseUrl}/{pdpId}} for each configured PDP
 * ID. Change detection uses HTTP conditional requests (ETag / If-None-Match).
 * </p>
 *
 * @param baseUrl
 * the base URL of the bundle server (e.g.,
 * {@code https://pap.example.com/bundles})
 * @param pdpIds
 * the list of PDP identifiers to fetch bundles for
 * @param mode
 * the change detection mode (polling or long-poll)
 * @param pollInterval
 * the default interval between polls (used in POLLING mode)
 * @param longPollTimeout
 * the server hold timeout for long-poll mode
 * @param authHeaderName
 * optional HTTP header name for authentication (e.g., {@code Authorization})
 * @param authHeaderValue
 * optional HTTP header value for authentication (e.g., {@code Bearer <token>})
 * @param allowInsecureHttp
 * whether authentication credentials may be sent over plaintext HTTP
 * @param followRedirects
 * whether to follow HTTP 3xx redirects
 * @param securityPolicy
 * the bundle signature verification policy
 * @param pdpIdPollIntervals
 * per-pdpId poll interval overrides
 * @param firstBackoff
 * initial backoff duration after a fetch failure
 * @param maxBackoff
 * maximum backoff duration after repeated failures
 */
@Slf4j
public record RemoteBundleSourceConfig(
        String baseUrl,
        List<String> pdpIds,
        FetchMode mode,
        Duration pollInterval,
        Duration longPollTimeout,
        @Nullable String authHeaderName,
        @Nullable String authHeaderValue,
        boolean allowInsecureHttp,
        boolean followRedirects,
        BundleSecurityPolicy securityPolicy,
        Map<String, Duration> pdpIdPollIntervals,
        Duration firstBackoff,
        Duration maxBackoff) {

    private static final String ERROR_AUTH_HEADER_INCOMPLETE = "Both authHeaderName and authHeaderValue must be provided together, or both must be null.";
    private static final String ERROR_BASE_URL_BLANK = "baseUrl must not be null or blank.";
    private static final String ERROR_FIRST_BACKOFF_NON_POSITIVE = "firstBackoff must be positive.";
    private static final String ERROR_INSECURE_CREDENTIAL_TRANSPORT = "Remote bundle credentials require https. Credentials over plaintext http are refused unless allowInsecureHttp is true.";
    private static final String ERROR_LONG_POLL_TIMEOUT_NON_POSITIVE = "longPollTimeout must be positive.";
    private static final String ERROR_MAX_BACKOFF_NON_POSITIVE = "maxBackoff must be positive.";
    private static final String ERROR_PDP_IDS_EMPTY = "pdpIds must not be null or empty.";
    private static final String ERROR_POLL_INTERVAL_NON_POSITIVE = "pollInterval must be positive.";
    private static final String WARN_CREDENTIALS_OVER_PLAINTEXT = "Bundle source sends an authentication credential to '{}' over an unencrypted (http) connection because allowInsecureHttp is true. The credential travels in cleartext and can be read by anything on the network path.";

    /**
     * Change detection mode for remote bundle fetching.
     */
    public enum FetchMode {
        /** Regular interval-based polling with ETag conditional requests. */
        POLLING,
        /** Long-poll GET with automatic reconnect on timeout. */
        LONG_POLL
    }

    /**
     * Compact constructor with fail-fast validation.
     */
    public RemoteBundleSourceConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PDPConfigurationException(ERROR_BASE_URL_BLANK);
        }
        if (pdpIds == null || pdpIds.isEmpty()) {
            throw new PDPConfigurationException(ERROR_PDP_IDS_EMPTY);
        }
        pdpIds.forEach(PdpIdValidator::validatePdpId);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(pollInterval, "pollInterval");
        Objects.requireNonNull(longPollTimeout, "longPollTimeout");
        Objects.requireNonNull(securityPolicy, "securityPolicy");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new PDPConfigurationException(ERROR_POLL_INTERVAL_NON_POSITIVE);
        }
        if (longPollTimeout.isNegative() || longPollTimeout.isZero()) {
            throw new PDPConfigurationException(ERROR_LONG_POLL_TIMEOUT_NON_POSITIVE);
        }
        if (firstBackoff == null || firstBackoff.isNegative() || firstBackoff.isZero()) {
            throw new PDPConfigurationException(ERROR_FIRST_BACKOFF_NON_POSITIVE);
        }
        if (maxBackoff == null || maxBackoff.isNegative() || maxBackoff.isZero()) {
            throw new PDPConfigurationException(ERROR_MAX_BACKOFF_NON_POSITIVE);
        }
        if ((authHeaderName == null) == (authHeaderValue != null)) {
            throw new PDPConfigurationException(ERROR_AUTH_HEADER_INCOMPLETE);
        }
        if (authHeaderValue != null) {
            enforceCredentialTransportSecurity(baseUrl, allowInsecureHttp);
        }
        pdpIds             = List.copyOf(pdpIds);
        pdpIdPollIntervals = pdpIdPollIntervals != null ? Map.copyOf(pdpIdPollIntervals) : Map.of();
    }

    public RemoteBundleSourceConfig(String baseUrl,
            List<String> pdpIds,
            FetchMode mode,
            Duration pollInterval,
            Duration longPollTimeout,
            @Nullable String authHeaderName,
            @Nullable String authHeaderValue,
            boolean followRedirects,
            BundleSecurityPolicy securityPolicy,
            Map<String, Duration> pdpIdPollIntervals,
            Duration firstBackoff,
            Duration maxBackoff) {
        this(baseUrl, pdpIds, mode, pollInterval, longPollTimeout, authHeaderName, authHeaderValue, false,
                followRedirects, securityPolicy, pdpIdPollIntervals, firstBackoff, maxBackoff);
    }

    // Redacts the credential so it never reaches logs, dumps, or exception
    // messages.
    @Override
    public String toString() {
        return "RemoteBundleSourceConfig[baseUrl=" + baseUrl + ", pdpIds=" + pdpIds + ", mode=" + mode
                + ", pollInterval=" + pollInterval + ", longPollTimeout=" + longPollTimeout + ", authHeaderName="
                + authHeaderName + ", authHeaderValue=" + (authHeaderValue == null ? null : "REDACTED")
                + ", allowInsecureHttp=" + allowInsecureHttp + ", followRedirects=" + followRedirects
                + ", securityPolicy=" + securityPolicy + ", pdpIdPollIntervals=" + pdpIdPollIntervals
                + ", firstBackoff=" + firstBackoff + ", maxBackoff=" + maxBackoff + "]";
    }

    /**
     * True when a credential sent to {@code baseUrl} would travel in cleartext.
     * That is the case when the URL does not use https.
     */
    static boolean credentialIsExposed(String baseUrl) {
        return !isEncryptedBaseUrl(baseUrl);
    }

    private static void enforceCredentialTransportSecurity(String baseUrl, boolean allowInsecureHttp) {
        if (!credentialIsExposed(baseUrl)) {
            return;
        }
        if (!allowInsecureHttp) {
            throw new PDPConfigurationException(ERROR_INSECURE_CREDENTIAL_TRANSPORT);
        }
        log.warn(WARN_CREDENTIALS_OVER_PLAINTEXT, baseUrl);
    }

    private static boolean isEncryptedBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        return baseUrl.regionMatches(true, 0, "https://", 0, "https://".length());
    }

}
