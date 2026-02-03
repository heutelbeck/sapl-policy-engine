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
package io.sapl.pdp.configuration.bundle;

import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.util.Set;

/**
 * Security policy for bundle signature verification.
 * <p>
 * This class enforces a <b>secure-by-default</b> approach to bundle loading.
 * Signature verification is mandatory unless
 * explicitly disabled with explicit risk acceptance. This two-factor opt-out
 * ensures operator understand the security
 * implications of loading unsigned bundles.
 * </p>
 * <h2>Security Model</h2>
 * <p>
 * Policy bundles contain access control rules that determine authorization
 * decisions. Loading tampered or malicious
 * policies could lead to:
 * </p>
 * <ul>
 * <li>Complete bypass of access control (e.g., injected "permit all"
 * policy)</li>
 * <li>Denial of service (e.g., injected "deny all" policy)</li>
 * <li>Data exfiltration via policy information point calls</li>
 * <li>Privilege escalation through crafted policy logic</li>
 * </ul>
 * <p>
 * Signature verification ensures bundles originate from a trusted source and
 * have not been modified in transit or at
 * rest.
 * </p>
 * <h2>Usage</h2>
 * <h3>Production (Recommended)</h3>
 *
 * <pre>{@code
 * // Load trusted public key from secure storage
 * PublicKey trustedKey = loadFromKeyStore();
 *
 * // Create policy requiring valid signatures
 * BundleSecurityPolicy policy = BundleSecurityPolicy.requireSignature(trustedKey);
 *
 * // With expiration enforcement
 * BundleSecurityPolicy policy = BundleSecurityPolicy.builder(trustedKey).withExpirationCheck().build();
 * }</pre>
 *
 * <h3>Development Only (Requires Explicit Risk Acceptance)</h3>
 *
 * <pre>{@code
 * // DANGER: Only for development environments
 * // Both flags must be explicitly set
 * BundleSecurityPolicy policy = BundleSecurityPolicy.builder().disableSignatureVerification()
 *         .acceptUnsignedBundleRisks().build();
 * }</pre>
 *
 * <h2>Integration with Spring</h2>
 * <p>
 * This class is Spring-agnostic. Spring applications should create a
 * configuration class that reads properties and
 * constructs the appropriate policy:
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Configuration
 *     public class BundleSecurityConfig {
 *
 *         &#64;Bean
 *         public BundleSecurityPolicy bundleSecurityPolicy(BundleSecurityProperties props,
 *                 AcceptedRisksProperties risks) {
 *
 *             if (props.isSignatureVerificationEnabled()) {
 *                 return BundleSecurityPolicy.builder(props.getPublicKey())
 *                         .withExpirationCheck(props.isCheckExpiration()).build();
 *             }
 *
 *             // Signature disabled - require risk acceptance
 *             return BundleSecurityPolicy.builder().disableSignatureVerification()
 *                     .acceptUnsignedBundleRisks(risks.isUnsignedBundles()).build();
 *         }
 *     }
 * }
 * </pre>
 *
 * @see BundleParser
 * @see BundleSigner
 */
@Slf4j
public final class BundleSecurityPolicy {

    private static final Set<String> ED25519_ALGORITHM_NAMES                          = Set.of("Ed25519", "EdDSA");
    private static final String      ERROR_BUNDLE_NOT_SIGNED_REQUIRED                 = "Bundle '%s' is not signed but signature verification is required.";
    private static final String      ERROR_BUNDLE_NOT_SIGNED_RISK_NOT_ACCEPTED        = "Bundle '%s' is not signed and unsigned bundle risks have not been accepted.";
    private static final String      ERROR_NO_PUBLIC_KEY_FOR_VERIFICATION             = "Bundle signature verification is required but no public key was provided.";
    private static final String      ERROR_PUBLIC_KEY_MUST_BE_ED25519                 = "Public key must be Ed25519, got: %s.";
    private static final String      ERROR_PUBLIC_KEY_MUST_NOT_BE_NULL                = "Public key must not be null.";
    private static final String      ERROR_SIGNATURE_DISABLED_WITHOUT_RISK_ACCEPTANCE = "Bundle signature verification disabled without risk acceptance. Set the appropriate configuration to accept unsigned bundle risks.";
    private static final String      ERROR_SIGNATURE_REQUIRES_PUBLIC_KEY              = "Signature verification requires a public key. Use builder(publicKey) or disable signature verification.";

    private final PublicKey publicKey;
    private final boolean   signatureRequired;
    private final boolean   checkExpiration;
    private final boolean   unsignedBundleRiskAccepted;

    private BundleSecurityPolicy(PublicKey publicKey,
            boolean signatureRequired,
            boolean checkExpiration,
            boolean unsignedBundleRiskAccepted) {
        this.publicKey                  = publicKey;
        this.signatureRequired          = signatureRequired;
        this.checkExpiration            = checkExpiration;
        this.unsignedBundleRiskAccepted = unsignedBundleRiskAccepted;
    }

    /**
     * Creates a security policy requiring valid Ed25519 signatures.
     * <p>
     * This is the recommended configuration for production environments. All
     * bundles must be signed with a key that can
     * be verified against the provided public key.
     * </p>
     *
     * @param publicKey
     * Ed25519 public key for signature verification
     *
     * @return security policy requiring signatures
     *
     * @throws IllegalArgumentException
     * if publicKey is null or not Ed25519
     */
    public static BundleSecurityPolicy requireSignature(PublicKey publicKey) {
        validatePublicKey(publicKey);
        return new BundleSecurityPolicy(publicKey, true, false, false);
    }

    /**
     * Creates a builder for custom security policy configuration.
     * <p>
     * Use this when you need signature verification with additional options like
     * expiration checking.
     * </p>
     *
     * @param publicKey
     * Ed25519 public key for signature verification
     *
     * @return builder for policy configuration
     *
     * @throws IllegalArgumentException
     * if publicKey is null or not Ed25519
     */
    public static Builder builder(PublicKey publicKey) {
        validatePublicKey(publicKey);
        return new Builder(publicKey);
    }

    /**
     * Creates a builder for security policy without a public key.
     * <p>
     * <b>WARNING:</b> This is intended only for explicitly disabling signature
     * verification in development
     * environments. Using this builder requires calling both
     * {@link Builder#disableSignatureVerification()} and
     * {@link Builder#acceptUnsignedBundleRisks()} to create a valid policy.
     * </p>
     *
     * @return builder for policy configuration
     */
    public static Builder builder() {
        return new Builder(null);
    }

    /**
     * Returns the public key for signature verification, or null if verification is
     * disabled.
     *
     * @return the Ed25519 public key, or null
     */
    public PublicKey publicKey() {
        return publicKey;
    }

    /**
     * Returns whether signature verification is required.
     *
     * @return true if bundles must be signed
     */
    public boolean signatureRequired() {
        return signatureRequired;
    }

    /**
     * Returns whether signature expiration should be checked.
     *
     * @return true if expired signatures should be rejected
     */
    public boolean checkExpiration() {
        return checkExpiration;
    }

    /**
     * Returns whether the operator has accepted the risks of loading unsigned
     * bundles.
     *
     * @return true if unsigned bundle risks are accepted
     */
    public boolean unsignedBundleRiskAccepted() {
        return unsignedBundleRiskAccepted;
    }

    /**
     * Validates this policy configuration and logs appropriate warnings.
     * <p>
     * This method should be called during application startup to ensure the
     * security policy is valid and to create an
     * audit trail in logs.
     * </p>
     *
     * @throws BundleSignatureException
     * if the policy is invalid (e.g., signature disabled without risk acceptance)
     */
    public void validate() {
        if (signatureRequired) {
            if (publicKey == null) {
                throw new BundleSignatureException(ERROR_NO_PUBLIC_KEY_FOR_VERIFICATION);
            }
            log.info(
                    "Bundle security policy: Signature verification ENABLED. "
                            + "All bundles must be signed with a trusted Ed25519 key. " + "Expiration check: {}.",
                    checkExpiration ? "enabled" : "disabled");
            return;
        }

        // Signature not required - this is a security risk
        if (!unsignedBundleRiskAccepted) {
            log.error("""
                    SECURITY VIOLATION: Bundle signature verification is disabled but the associated \
                    risks have not been accepted. The application will refuse to load bundles until \
                    either signature verification is enabled OR the risks are explicitly accepted. \
                    To accept risks, ensure both conditions are met in configuration: \
                    (1) signature verification disabled, AND \
                    (2) unsigned bundle risks accepted.""");
            throw new BundleSignatureException(ERROR_SIGNATURE_DISABLED_WITHOUT_RISK_ACCEPTANCE);
        }

        // Risk accepted - log warning for audit trail
        log.warn("""
                SECURITY WARNING: Bundle signature verification is DISABLED. \
                The server administrator has explicitly accepted the following risks: \
                (1) Bundles may be loaded from untrusted sources. \
                (2) Bundles may have been tampered with in transit or at rest. \
                (3) Malicious policies could bypass access control, cause denial of service, \
                    or lead to data exfiltration. \
                This configuration should ONLY be used in isolated development environments.""");
    }

    /**
     * Checks if an unsigned bundle should be accepted based on this policy.
     * <p>
     * This method logs appropriate warnings and throws exceptions based on the
     * policy configuration.
     * </p>
     *
     * @param bundleIdentifier
     * identifier for the bundle (for logging)
     *
     * @throws BundleSignatureException
     * if unsigned bundles are not allowed
     */
    public void checkUnsignedBundleAllowed(String bundleIdentifier) {
        if (signatureRequired) {
            throw new BundleSignatureException(ERROR_BUNDLE_NOT_SIGNED_REQUIRED.formatted(bundleIdentifier));
        }

        if (!unsignedBundleRiskAccepted) {
            throw new BundleSignatureException(ERROR_BUNDLE_NOT_SIGNED_RISK_NOT_ACCEPTED.formatted(bundleIdentifier));
        }

        log.warn("SECURITY: Loading unsigned bundle '{}'. Policy integrity and authenticity are NOT verified.",
                bundleIdentifier);
    }

    private static void validatePublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException(ERROR_PUBLIC_KEY_MUST_NOT_BE_NULL);
        }
        if (!ED25519_ALGORITHM_NAMES.contains(publicKey.getAlgorithm())) {
            throw new IllegalArgumentException(ERROR_PUBLIC_KEY_MUST_BE_ED25519.formatted(publicKey.getAlgorithm()));
        }
    }

    /**
     * Builder for creating custom security policies.
     */
    public static final class Builder {

        private final PublicKey publicKey;
        private boolean         signatureRequired          = true;
        private boolean         checkExpiration            = false;
        private boolean         unsignedBundleRiskAccepted = false;

        private Builder(PublicKey publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * Enables signature expiration checking.
         * <p>
         * When enabled, signatures with an expiration timestamp will be rejected if the
         * current time is past the
         * expiration. This enforces regular bundle refresh and limits the validity
         * window of potentially compromised
         * bundles.
         * </p>
         *
         * @return this builder
         */
        public Builder withExpirationCheck() {
            this.checkExpiration = true;
            return this;
        }

        /**
         * Enables or disables signature expiration checking.
         *
         * @param check
         * true to enable expiration checking
         *
         * @return this builder
         */
        public Builder withExpirationCheck(boolean check) {
            this.checkExpiration = check;
            return this;
        }

        /**
         * Disables signature verification.
         * <p>
         * <b>DANGER:</b> This allows loading of unsigned bundles, which means:
         * </p>
         * <ul>
         * <li>Bundle origin cannot be verified</li>
         * <li>Bundle integrity cannot be verified</li>
         * <li>Malicious policies may be loaded</li>
         * </ul>
         * <p>
         * You must also call {@link #acceptUnsignedBundleRisks()} to build a valid
         * policy with this setting.
         * </p>
         *
         * @return this builder
         */
        public Builder disableSignatureVerification() {
            this.signatureRequired = false;
            return this;
        }

        /**
         * Explicitly accepts the risks of loading unsigned bundles.
         * <p>
         * This is a required second factor when disabling signature verification. It
         * creates an explicit audit trail
         * showing the operator understood and accepted the security implications.
         * </p>
         *
         * @return this builder
         */
        public Builder acceptUnsignedBundleRisks() {
            this.unsignedBundleRiskAccepted = true;
            return this;
        }

        /**
         * Sets whether unsigned bundle risks are accepted.
         * <p>
         * This method is useful when the acceptance flag comes from external
         * configuration (e.g., Spring properties).
         * </p>
         *
         * @param accepted
         * true if risks are accepted
         *
         * @return this builder
         */
        public Builder acceptUnsignedBundleRisks(boolean accepted) {
            this.unsignedBundleRiskAccepted = accepted;
            return this;
        }

        /**
         * Builds the security policy.
         * <p>
         * The resulting policy is validated to ensure consistent configuration. If
         * signature verification is enabled, a
         * public key must have been provided. If signature verification is disabled,
         * risks must be explicitly accepted.
         * </p>
         *
         * @return the configured security policy
         *
         * @throws IllegalStateException
         * if the configuration is invalid
         */
        public BundleSecurityPolicy build() {
            if (signatureRequired && publicKey == null) {
                throw new IllegalStateException(ERROR_SIGNATURE_REQUIRES_PUBLIC_KEY);
            }

            return new BundleSecurityPolicy(publicKey, signatureRequired, checkExpiration, unsignedBundleRiskAccepted);
        }
    }

}
