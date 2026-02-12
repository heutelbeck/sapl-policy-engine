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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BundleSecurityPolicy")
class BundleSecurityPolicyTests {

    private static final CombiningAlgorithm DENY_OVERRIDES      = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_OVERRIDES    = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_UNLESS_PERMIT  = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm ONLY_ONE_APPLICABLE = new CombiningAlgorithm(VotingMode.UNIQUE,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private static KeyPair elderKeyPair;
    private static KeyPair stagingKeyPair;

    @BeforeAll
    static void setupKeys() throws NoSuchAlgorithmException {
        val generator = KeyPairGenerator.getInstance("Ed25519");
        elderKeyPair   = generator.generateKeyPair();
        stagingKeyPair = generator.generateKeyPair();
    }

    @Test
    void whenBuildingWithPublicKeyThenSignatureIsRequired() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();

        assertThat(policy.signatureRequired()).isTrue();
        assertThat(policy.publicKey()).isEqualTo(elderKeyPair.getPublic());
        assertThat(policy.unsignedBundleRiskAccepted()).isFalse();
    }

    @Test
    void whenBuilderWithNullKeyThenThrowsException() {
        assertThatThrownBy(() -> BundleSecurityPolicy.builder(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Public key must not be null");
    }

    @Test
    void whenDisablingSignatureWithoutAcceptingRisksThenBuildSucceedsButValidateFails() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThat(policy.signatureRequired()).isFalse();
        assertThat(policy.unsignedBundleRiskAccepted()).isFalse();

        assertThatThrownBy(policy::validate).isInstanceOf(BundleSignatureException.class)
                .hasMessageContaining("disabled without risk acceptance");
    }

    @Test
    void whenDisablingSignatureAndAcceptingRisksThenValidateSucceeds() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        assertThat(policy.signatureRequired()).isFalse();
        assertThat(policy.unsignedBundleRiskAccepted()).isTrue();

        policy.validate();
    }

    @Test
    void whenAcceptingRisksWithoutDisablingSignatureThenValidateSucceeds() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).acceptUnsignedBundleRisks().build();

        assertThat(policy.signatureRequired()).isTrue();

        policy.validate();
    }

    @Test
    void whenSignatureRequiredButNoKeyThenBuildFails() {
        val builder = BundleSecurityPolicy.builder();
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires a public key");
    }

    @Test
    void whenParsingUnsignedBundleWithRequiredSignatureThenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                .withPolicy("cultist-access.sapl", "policy \"cultist\" permit subject.initiated == true").build();

        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "test-pdp", policy))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
    }

    @Test
    void whenParsingUnsignedBundleWithDisabledVerificationAndAcceptedRisksThenSucceeds() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(PERMIT_OVERRIDES)
                .withPolicy("public-access.sapl", "policy \"public\" permit true").build();

        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        val config = BundleParser.parse(bundle, "test-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("test-pdp");
    }

    @Test
    void whenParsingUnsignedBundleWithDisabledVerificationButNoAcceptedRisksThenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(PERMIT_OVERRIDES)
                .withPolicy("public-access.sapl", "policy \"public\" permit true").build();

        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "test-pdp", policy))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not been accepted");
    }

    @Test
    void whenParsingSignedBundleWithValidKeyThenSucceeds() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_UNLESS_PERMIT)
                .withPolicy("necronomicon.sapl", "policy \"tome\" deny subject.sanity < 50")
                .signWith(elderKeyPair.getPrivate(), "elder-key").build();

        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();
        val config = BundleParser.parse(bundle, "library-pdp", policy);

        assertThat(config.pdpId()).isEqualTo("library-pdp");
        assertThat(config.combiningAlgorithm()).isEqualTo(DENY_UNLESS_PERMIT);
    }

    @Test
    void whenParsingSignedBundleWithWrongKeyThenThrowsException() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(ONLY_ONE_APPLICABLE)
                .withPolicy("ritual.sapl", "policy \"ritual\" permit action.type == \"summon\"")
                .signWith(elderKeyPair.getPrivate(), "elder-key").build();

        val wrongKeyPair = generateEd25519KeyPair();
        val policy       = BundleSecurityPolicy.builder(wrongKeyPair.getPublic()).build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "cult-pdp", policy))
                .isInstanceOf(BundleSignatureException.class);
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithSignatureRequiredThenThrows() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();

        assertThatThrownBy(() -> policy.checkUnsignedBundleAllowed("test-bundle"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed")
                .hasMessageContaining("signature verification is required");
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithDisabledVerificationButNoRiskAcceptanceThenThrows() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThatThrownBy(() -> policy.checkUnsignedBundleAllowed("test-bundle"))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not been accepted");
    }

    @Test
    void whenCheckUnsignedBundleAllowedWithFullOptOutThenSucceeds() {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();

        policy.checkUnsignedBundleAllowed("test-bundle");
    }

    @Test
    void whenValidateWithRequiredSignatureAndKeyThenSucceeds() {
        val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();

        policy.validate();
    }

    @Test
    void whenAcceptUnsignedBundleRisksBooleanThenSetsCorrectly() {
        val policyAccepted = BundleSecurityPolicy.builder().disableSignatureVerification()
                .acceptUnsignedBundleRisks(true).build();

        val policyNotAccepted = BundleSecurityPolicy.builder().disableSignatureVerification()
                .acceptUnsignedBundleRisks(false).build();

        assertThat(policyAccepted.unsignedBundleRiskAccepted()).isTrue();
        assertThat(policyNotAccepted.unsignedBundleRiskAccepted()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonEd25519KeyAlgorithms")
    void whenBuilderWithNonEd25519KeyThenThrowsException(String algorithm) throws Exception {
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        val keyPair          = keyPairGenerator.generateKeyPair();
        val publicKey        = keyPair.getPublic();
        assertThatThrownBy(() -> BundleSecurityPolicy.builder(publicKey)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ed25519");
    }

    static Stream<Arguments> nonEd25519KeyAlgorithms() {
        return Stream.of(arguments("RSA"), arguments("EC"));
    }

    @Nested
    @DisplayName("per-tenant key resolution")
    class PerTenantKeyResolutionTests {

        @Test
        @DisplayName("resolves key from tenant trust when tenant is configured")
        void whenTenantConfiguredThenResolvesFromCatalogue() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThat(policy.resolvePublicKey("production", "prod-key")).isEqualTo(elderKeyPair.getPublic());
        }

        @Test
        @DisplayName("falls back to global key when tenant is not configured")
        void whenTenantNotConfiguredThenFallsBackToGlobalKey() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder(stagingKeyPair.getPublic()).withKeyCatalogue(catalogue)
                    .withTenantTrust(tenantTrust).build();

            assertThat(policy.resolvePublicKey("staging", "any-key")).isEqualTo(stagingKeyPair.getPublic());
        }

        @Test
        @DisplayName("rejects key not trusted for configured tenant")
        void whenKeyNotTrustedForTenantThenThrows() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic(), "staging-key", stagingKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThatThrownBy(() -> policy.resolvePublicKey("production", "staging-key"))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not trusted")
                    .hasMessageContaining("production");
        }

        @Test
        @DisplayName("throws when no global key and tenant not configured")
        void whenNoGlobalKeyAndTenantNotConfiguredThenThrows() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThatThrownBy(() -> policy.resolvePublicKey("unknown-tenant", "some-key"))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("No public key available")
                    .hasMessageContaining("unknown-tenant");
        }

        @Test
        @DisplayName("validates tenant trust references during validate()")
        void whenTenantReferencesInvalidKeyThenValidateFails() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key", "nonexistent-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThatThrownBy(policy::validate).isInstanceOf(BundleSignatureException.class)
                    .hasMessageContaining("nonexistent-key").hasMessageContaining("not in the key catalogue");
        }

        @Test
        @DisplayName("builds with catalogue-only (no global key)")
        void whenCatalogueOnlyThenBuildSucceeds() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThat(policy.signatureRequired()).isTrue();
            assertThat(policy.publicKey()).isNull();
            policy.validate();
        }

        @Test
        @DisplayName("supports multiple keys per tenant")
        void whenMultipleKeysPerTenantThenAllAccepted() {
            val catalogue   = Map.of("key-2025", elderKeyPair.getPublic(), "key-2026", stagingKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("key-2025", "key-2026"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            assertThat(policy.resolvePublicKey("production", "key-2025")).isEqualTo(elderKeyPair.getPublic());
            assertThat(policy.resolvePublicKey("production", "key-2026")).isEqualTo(stagingKeyPair.getPublic());
        }

        @Test
        @DisplayName("cross-tenant bundle rejection via parse")
        void whenBundleSignedWithWrongTenantKeyThenParseThrows() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic(), "staging-key", stagingKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"), "staging", Set.of("staging-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("test.sapl", "policy \"test\" permit true")
                    .signWith(stagingKeyPair.getPrivate(), "staging-key").build();

            assertThatThrownBy(() -> BundleParser.parse(bundle, "production", policy))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not trusted")
                    .hasMessageContaining("production");
        }

        @Test
        @DisplayName("per-tenant signed bundle accepted when key matches")
        void whenBundleSignedWithCorrectTenantKeyThenParseSucceeds() {
            val catalogue   = Map.of("prod-key", elderKeyPair.getPublic());
            val tenantTrust = Map.of("production", Set.of("prod-key"));

            val policy = BundleSecurityPolicy.builder().withKeyCatalogue(catalogue).withTenantTrust(tenantTrust)
                    .build();

            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("test.sapl", "policy \"test\" permit true")
                    .signWith(elderKeyPair.getPrivate(), "prod-key").build();

            val config = BundleParser.parse(bundle, "production", policy);

            assertThat(config.pdpId()).isEqualTo("production");
        }

        @Test
        @DisplayName("null catalogue treated as empty")
        void whenNullCatalogueThenTreatedAsEmpty() {
            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withKeyCatalogue(null)
                    .withTenantTrust(null).build();

            assertThat(policy.resolvePublicKey("any-tenant", "any-key")).isEqualTo(elderKeyPair.getPublic());
        }

    }

    @Nested
    @DisplayName("per-tenant unsigned opt-out")
    class PerTenantUnsignedTests {

        @Test
        @DisplayName("unsigned bundle accepted for tenant in unsigned-tenants list")
        void whenTenantInUnsignedListThenUnsignedBundleAccepted() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("dev-policy.sapl", "policy \"dev\" permit true").build();

            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic())
                    .withUnsignedTenants(Set.of("development")).build();

            val config = BundleParser.parse(bundle, "development", policy);

            assertThat(config.pdpId()).isEqualTo("development");
        }

        @Test
        @DisplayName("unsigned bundle rejected for tenant not in unsigned-tenants list")
        void whenTenantNotInUnsignedListThenUnsignedBundleRejected() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("prod-policy.sapl", "policy \"prod\" deny true").build();

            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic())
                    .withUnsignedTenants(Set.of("development")).build();

            assertThatThrownBy(() -> BundleParser.parse(bundle, "production", policy))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
        }

        @Test
        @DisplayName("empty unsigned-tenants list rejects all unsigned bundles")
        void whenEmptyUnsignedListThenAllUnsignedRejected() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(PERMIT_OVERRIDES)
                    .withPolicy("test.sapl", "policy \"test\" permit true").build();

            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withUnsignedTenants(Set.of()).build();

            assertThatThrownBy(() -> BundleParser.parse(bundle, "any-tenant", policy))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
        }

        @Test
        @DisplayName("global unsigned override still works alongside unsigned-tenants")
        void whenGlobalUnsignedEnabledThenOverridesPerTenant() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("global.sapl", "policy \"global\" permit true").build();

            val policy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                    .build();

            val config = BundleParser.parse(bundle, "any-unlisted-tenant", policy);

            assertThat(config.pdpId()).isEqualTo("any-unlisted-tenant");
        }

        @Test
        @DisplayName("signed bundle still works for tenant in unsigned-tenants list")
        void whenTenantInUnsignedListThenSignedBundleStillAccepted() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_UNLESS_PERMIT)
                    .withPolicy("signed-dev.sapl", "policy \"dev\" permit true")
                    .signWith(elderKeyPair.getPrivate(), "dev-key").build();

            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic())
                    .withUnsignedTenants(Set.of("development")).build();

            val config = BundleParser.parse(bundle, "development", policy);

            assertThat(config.pdpId()).isEqualTo("development");
        }

        @Test
        @DisplayName("null unsigned-tenants treated as empty")
        void whenNullUnsignedTenantsThenTreatedAsEmpty() {
            val policy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).withUnsignedTenants(null).build();

            val bundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                    .withPolicy("test.sapl", "policy \"test\" permit true").build();

            assertThatThrownBy(() -> BundleParser.parse(bundle, "any-tenant", policy))
                    .isInstanceOf(BundleSignatureException.class).hasMessageContaining("not signed");
        }

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
