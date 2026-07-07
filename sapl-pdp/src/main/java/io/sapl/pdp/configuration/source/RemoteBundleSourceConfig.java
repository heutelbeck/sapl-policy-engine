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
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.net.URI;
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
 * @param realm
 * the realm identifier (required in {@code MULTI} mode; the index must declare
 * this realm)
 * @param indexPath
 * the path of the realm index, appended to {@code baseUrl} (required in
 * {@code MULTI} mode)
 * @param staleAfterNoContact
 * how long a pdpId may go without a successful contact (a {@code 200} or
 * {@code 304}) before it is marked {@code STALE} and reported in health while
 * still serving its last-good configuration; when {@code null} a default of
 * {@code max(5 x effective poll cadence, 60s)} is derived
 * @param failClosedAfterNoContact
 * how long a pdpId may go without a successful contact before it fails closed
 * (stops serving its last-good configuration and denies), or {@code null} to
 * never fail closed on staleness; when set it must be greater than
 * {@code staleAfterNoContact}
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
        Duration maxBackoff,
        @Nullable String realm,
        @Nullable String indexPath,
        Duration staleAfterNoContact,
        @Nullable Duration failClosedAfterNoContact) {

    private static final int STALE_MULTIPLIER = 5;
    private static final Duration STALE_FLOOR = Duration.ofSeconds(60);

    private static final String ERROR_AUTH_HEADER_INCOMPLETE = "Both authHeaderName and authHeaderValue must be provided together, or both must be null.";
    private static final String ERROR_BASE_URL_BLANK = "baseUrl must not be null or blank.";
    private static final String ERROR_BASE_URL_INVALID = "baseUrl must be a valid URI.";
    private static final String ERROR_BASE_URL_USERINFO = "baseUrl must not contain URI userinfo.";
    private static final String ERROR_FAIL_CLOSED_NOT_AFTER_STALE = "failClosedAfterNoContact must be greater than staleAfterNoContact.";
    private static final String ERROR_FIRST_BACKOFF_NON_POSITIVE = "firstBackoff must be positive.";
    private static final String ERROR_INDEX_PATH_BLANK = "indexPath must not be null or blank when mode is MULTI.";
    private static final String ERROR_INSECURE_CREDENTIAL_TRANSPORT = "Remote bundle credentials require https. Credentials over plaintext http are refused unless allowInsecureHttp is true.";
    private static final String ERROR_LONG_POLL_TIMEOUT_NON_POSITIVE = "longPollTimeout must be positive.";
    private static final String ERROR_MAX_BACKOFF_NON_POSITIVE = "maxBackoff must be positive.";
    private static final String ERROR_PDP_ID_POLL_INTERVAL_NON_POSITIVE = "pdpIdPollIntervals for pdpId '%s' must be positive.";
    private static final String ERROR_PDP_ID_POLL_INTERVAL_UNKNOWN_ID = "pdpIdPollIntervals contains unknown pdpId '%s'.";
    private static final String ERROR_PDP_IDS_EMPTY = "pdpIds must not be null or empty.";
    private static final String ERROR_POLL_INTERVAL_NON_POSITIVE = "pollInterval must be positive.";
    private static final String ERROR_REALM_BLANK = "realm must not be null or blank when mode is MULTI.";
    private static final String ERROR_STALE_AFTER_NON_POSITIVE = "staleAfterNoContact must be positive.";
    private static final String WARN_CREDENTIALS_OVER_PLAINTEXT = "Bundle source sends an authentication credential to '{}' over an unencrypted (http) connection because allowInsecureHttp is true. The credential travels in cleartext and can be read by anything on the network path.";

    /**
     * Change detection mode for remote bundle fetching.
     */
    public enum FetchMode {
        /** Regular interval-based polling with ETag conditional requests. */
        POLLING,
        /** Long-poll GET with automatic reconnect on timeout. */
        LONG_POLL,
        /**
         * Realm mode: monitor a signed realm index and fetch the dynamic set of
         * bundles it lists, tracking additions, removals, and version changes.
         */
        MULTI
    }

    /**
     * Compact constructor with fail-fast validation.
     */
    public RemoteBundleSourceConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PDPConfigurationException(ERROR_BASE_URL_BLANK);
        }
        rejectUserInfo(baseUrl);
        Objects.requireNonNull(mode, "mode");
        if (mode == FetchMode.MULTI) {
            if (realm == null || realm.isBlank()) {
                throw new PDPConfigurationException(ERROR_REALM_BLANK);
            }
            if (indexPath == null || indexPath.isBlank()) {
                throw new PDPConfigurationException(ERROR_INDEX_PATH_BLANK);
            }
        } else if (pdpIds == null || pdpIds.isEmpty()) {
            throw new PDPConfigurationException(ERROR_PDP_IDS_EMPTY);
        }
        pdpIds = pdpIds == null ? List.of() : List.copyOf(pdpIds);
        pdpIds.forEach(PdpIdValidator::validatePdpId);
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
        if (pdpIdPollIntervals == null) {
            pdpIdPollIntervals = Map.of();
        } else {
            validatePdpIdPollIntervals(pdpIds, pdpIdPollIntervals);
            pdpIdPollIntervals = Map.copyOf(pdpIdPollIntervals);
        }
        staleAfterNoContact = staleAfterNoContact != null ? staleAfterNoContact
                : defaultStaleAfterNoContact(mode, pollInterval, longPollTimeout);
        if (staleAfterNoContact.isNegative() || staleAfterNoContact.isZero()) {
            throw new PDPConfigurationException(ERROR_STALE_AFTER_NON_POSITIVE);
        }
        if (failClosedAfterNoContact != null && failClosedAfterNoContact.compareTo(staleAfterNoContact) <= 0) {
            throw new PDPConfigurationException(ERROR_FAIL_CLOSED_NOT_AFTER_STALE);
        }
    }

    // Derives the freshness-warning threshold from the effective poll cadence when the
    // operator did not set one, floored so a fast poller does not warn on a brief blip.
    private static Duration defaultStaleAfterNoContact(FetchMode mode, Duration pollInterval,
            Duration longPollTimeout) {
        final Duration cadence = mode == FetchMode.LONG_POLL ? longPollTimeout : pollInterval;
        final Duration derived = cadence.multipliedBy(STALE_MULTIPLIER);
        return derived.compareTo(STALE_FLOOR) > 0 ? derived : STALE_FLOOR;
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
                followRedirects, securityPolicy, pdpIdPollIntervals, firstBackoff, maxBackoff, null, null, null, null);
    }

    /**
     * Convenience constructor for single-mode sources without a realm or index
     * path.
     */
    public RemoteBundleSourceConfig(String baseUrl,
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
        this(baseUrl, pdpIds, mode, pollInterval, longPollTimeout, authHeaderName, authHeaderValue, allowInsecureHttp,
                followRedirects, securityPolicy, pdpIdPollIntervals, firstBackoff, maxBackoff, null, null, null, null);
    }

    /**
     * Convenience constructor that derives the freshness thresholds: the default
     * freshness-warning threshold and no fail-closed on staleness.
     */
    public RemoteBundleSourceConfig(String baseUrl,
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
            Duration maxBackoff,
            @Nullable String realm,
            @Nullable String indexPath) {
        this(baseUrl, pdpIds, mode, pollInterval, longPollTimeout, authHeaderName, authHeaderValue, allowInsecureHttp,
                followRedirects, securityPolicy, pdpIdPollIntervals, firstBackoff, maxBackoff, realm, indexPath, null,
                null);
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
                + ", firstBackoff=" + firstBackoff + ", maxBackoff=" + maxBackoff + ", realm=" + realm + ", indexPath="
                + indexPath + "]";
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

    private static void rejectUserInfo(String baseUrl) {
        try {
            val uri = URI.create(baseUrl);
            if (uri.getRawUserInfo() != null) {
                throw new PDPConfigurationException(ERROR_BASE_URL_USERINFO);
            }
        } catch (IllegalArgumentException e) {
            throw new PDPConfigurationException(ERROR_BASE_URL_INVALID, e);
        }
    }

    private static boolean isEncryptedBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        return baseUrl.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    private static void validatePdpIdPollIntervals(List<String> pdpIds, Map<String, Duration> intervals) {
        for (val entry : intervals.entrySet()) {
            if (!pdpIds.contains(entry.getKey())) {
                throw new PDPConfigurationException(ERROR_PDP_ID_POLL_INTERVAL_UNKNOWN_ID.formatted(entry.getKey()));
            }
            val interval = entry.getValue();
            if (interval == null || interval.isNegative() || interval.isZero()) {
                throw new PDPConfigurationException(ERROR_PDP_ID_POLL_INTERVAL_NON_POSITIVE.formatted(entry.getKey()));
            }
        }
    }

}
