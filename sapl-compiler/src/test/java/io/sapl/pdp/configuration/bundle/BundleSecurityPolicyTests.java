/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.pdp.CombiningAlgorithm;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BundleSecurityPolicyTests {

    private static KeyPair elderKeyPair;

    @BeforeAll
    static void setupKeys() throws NoSuchAlgorithmException {
        val generator = KeyPairGenerator.getInstance("Ed25519");
        elderKeyPair = generator.generateKeyPair();
    }

    @Test
    void whenCreatingRequireSignature_thenSignatureIsRequired() {
        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());

        assertThat(policy.signatureRequired()).isTrue();
        assertThat(policy.publicKey()).isEqualTo(elderKeyPair.getPublic());
        assertThat(policy.checkExpiration()).isFalse();
        assertThat(policy.unsignedBundleRiskAccepted()).isFalse();
    }

    @Test
    void whenEnablingExpirationCheck_thenExpirationIsChecked() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withExpirationCheck().build();

        assertThat(policy.signatureRequired()).isTrue();
        assertThat(policy.checkExpiration()).isTrue();
    }

    @Test
    void whenRequireSignatureWithNullKey_thenThrowsException() {
        assertThatThrownBy(() -> BundleSecurityPolicy.requireSignature(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Public key must not be null");
    }

    @Test
    void whenBuilderWithNullKey_thenThrowsException() {
        assertThatThrownBy(() -> BundleSecurityPolicy.builder(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Public key must not be null");
    }

    @Test
    void whenDisablingSignatureWithoutAcceptingRisks_thenBuildSucceedsButValidateFails() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThat(policy.signatureRequired()).isFalse();
        assertThat(policy.unsignedBundleRiskAccepted()).isFalse();

        assertThatThrownBy(policy::validate).isInstanceOf(BundleSignatureException.class)
                .hasMessageContaining("disabled without risk acceptance");
    }

    @Test
    void whenDisablingSignatureAndAcceptingRisks_thenValidateSucceeds() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        assertThat(policy.signatureRequired()).isFalse();
        assertThat(policy.unsignedBundleRiskAccepted()).isTrue();

        policy.validate();
    }

    @Test
    void whenAcceptingRisksWithoutDisablingSignature_thenValidateSucceeds() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).acceptUnsignedBundleRisks().build();

        assertThat(policy.signatureRequired()).isTrue();

        policy.validate();
    }

    @Test
    void whenSignatureRequiredButNoKey_thenBuildFails() {
        assertThatThrownBy(() -> BundleSecurityPolicy.builder().build()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires a public key");
    }

    @Test
    void whenParsingUnsignedBundleWithRequiredSignature_thenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES)
                .withPolicy("cultist-access.sapl", "policy \"cultist\" permit subject.initiated == true").build();

        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());

        assertThatThrownBy(() -> BundleParser.parse(bundle, "test-pdp", policy))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
    }

    @Test
    void whenParsingUnsignedBundleWithDisabledVerificationAndAcceptedRisks_thenSucceeds() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES)
                .withPolicy("public-access.sapl", "policy \"public\" permit true").build();

        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        val config = BundleParser.parse(bundle, "test-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("test-pdp");
    }

    @Test
    void whenParsingUnsignedBundleWithDisabledVerificationButNoAcceptedRisks_thenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES)
                .withPolicy("public-access.sapl", "policy \"public\" permit true").build();

        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "test-pdp", policy))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not been accepted");
    }

    @Test
    void whenParsingSignedBundleWithValidKey_thenSucceeds() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_UNLESS_PERMIT)
                .withPolicy("necronomicon.sapl", "policy \"tome\" deny subject.sanity < 50")
                .signWith(elderKeyPair.getPrivate(), "elder-key").build();

        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());
        val config = BundleParser.parse(bundle, "library-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("library-pdp");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_UNLESS_PERMIT);
    }

    @Test
    void whenParsingSignedBundleWithWrongKey_thenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.ONLY_ONE_APPLICABLE)
                .withPolicy("ritual.sapl", "policy \"ritual\" permit action.type == \"summon\"")
                .signWith(elderKeyPair.getPrivate(), "elder-key").build();

        val wrongKeyPair = generateEd25519KeyPair();
        val policy       = BundleSecurityPolicy.requireSignature(wrongKeyPair.getPublic());

        assertThatThrownBy(() -> BundleParser.parse(bundle, "cult-pdp", policy))
                .isInstanceOf(BundleSignatureException.class);
    }

    @Test
    void whenParsingExpiredSignatureWithExpirationCheck_thenThrowsException() {
        val expiredTime = Instant.now().minus(1, ChronoUnit.DAYS);
        val bundle      = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES)
                .withPolicy("old-tome.sapl", "policy \"ancient\" deny true")
                .signWith(elderKeyPair.getPrivate(), "expired-key").expiresAt(expiredTime).build();

        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withExpirationCheck().build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "archive-pdp", policy))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("expired");
    }

    @Test
    void whenParsingExpiredSignatureWithoutExpirationCheck_thenSucceeds() {
        val expiredTime = Instant.now().minus(1, ChronoUnit.DAYS);
        val bundle      = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES)
                .withPolicy("ancient-scroll.sapl", "policy \"scroll\" permit subject.scholar == true")
                .signWith(elderKeyPair.getPrivate(), "ancient-key").expiresAt(expiredTime).build();

        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());
        val config = BundleParser.parse(bundle, "museum-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("museum-pdp");
    }

    @Test
    void whenParsingValidFutureExpiration_thenSucceeds() {
        val futureTime = Instant.now().plus(365, ChronoUnit.DAYS);
        val bundle     = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_UNLESS_PERMIT)
                .withPolicy("prophecy.sapl", "policy \"stars\" permit environment.starsRight == true")
                .signWith(elderKeyPair.getPrivate(), "prophecy-key").expiresAt(futureTime).build();

        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withExpirationCheck().build();
        val config = BundleParser.parse(bundle, "oracle-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("oracle-pdp");
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithSignatureRequired_thenThrows() {
        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());

        assertThatThrownBy(() -> policy.checkUnsignedBundleAllowed("test-bundle"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed")
                .hasMessageContaining("signature verification is required");
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithDisabledVerificationButNoRiskAcceptance_thenThrows() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThatThrownBy(() -> policy.checkUnsignedBundleAllowed("test-bundle"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not been accepted");
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithFullOptOut_thenSucceeds() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        policy.checkUnsignedBundleAllowed("test-bundle");
    }

    @Test
    void whenValidateWithRequiredSignatureAndKey_thenSucceeds() {
        val policy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());

        policy.validate();
    }

    @Test
    void whenWithExpirationCheckBoolean_thenSetsCorrectly() {
        val policyWithCheck = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withExpirationCheck(true).build();

        val policyWithoutCheck = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withExpirationCheck(false)
                .build();

        assertThat(policyWithCheck.checkExpiration()).isTrue();
        assertThat(policyWithoutCheck.checkExpiration()).isFalse();
    }

    @Test
    void whenAcceptUnsignedBundleRisksBoolean_thenSetsCorrectly() {
        val policyAccepted = BundleSecurityPolicy.builder().disableSignatureVerification()
                .acceptUnsignedBundleRisks(true).build();

        val policyNotAccepted = BundleSecurityPolicy.builder().disableSignatureVerification()
                .acceptUnsignedBundleRisks(false).build();

        assertThat(policyAccepted.unsignedBundleRiskAccepted()).isTrue();
        assertThat(policyNotAccepted.unsignedBundleRiskAccepted()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("nonEd25519KeyAlgorithms")
    void whenRequireSignatureWithNonEd25519Key_thenThrowsException(String algorithm) throws Exception {
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        val keyPair          = keyPairGenerator.generateKeyPair();

        assertThatThrownBy(() -> BundleSecurityPolicy.requireSignature(keyPair.getPublic()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ed25519");
    }

    static Stream<Arguments> nonEd25519KeyAlgorithms() {
        return Stream.of(arguments("RSA"), arguments("EC"));
    }

    private KeyPair generateEd25519KeyPair() {
        try {
            val generator = KeyPairGenerator.getInstance("Ed25519");
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available.", e);
        }
    }

}
