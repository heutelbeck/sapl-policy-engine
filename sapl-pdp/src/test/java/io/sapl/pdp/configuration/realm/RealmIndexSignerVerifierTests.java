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
package io.sapl.pdp.configuration.realm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;

import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;

import tools.jackson.databind.json.JsonMapper;

import lombok.val;

@DisplayName("RealmIndex signing and verification")
class RealmIndexSignerVerifierTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static KeyPair              trustedKeys;
    private static KeyPair              untrustedKeys;
    private static BundleSecurityPolicy signedPolicy;
    private static BundleSecurityPolicy developmentPolicy;

    @BeforeAll
    static void setup() throws NoSuchAlgorithmException {
        val generator = KeyPairGenerator.getInstance("Ed25519");
        trustedKeys       = generator.generateKeyPair();
        untrustedKeys     = generator.generateKeyPair();
        signedPolicy      = BundleSecurityPolicy.builder(trustedKeys.getPublic()).build();
        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
    }

    private static RealmIndex sampleIndex(long sequence) {
        return new RealmIndex("acme", sequence, "2026-07-04T12:31:00Z",
                List.of(new RealmIndexEntry("odoo-erp", "https://regent.example.com/realms/acme/bundles/odoo-erp")));
    }

    @Test
    @DisplayName("a signed index round-trips through verification")
    void whenSignedThenVerifyReturnsIndex() {
        val jws   = RealmIndexSigner.sign(sampleIndex(10), trustedKeys.getPrivate(), "k1");
        val index = RealmIndexVerifier.verify(jws, signedPolicy, "acme", 5);
        assertThat(index.realm()).isEqualTo("acme");
        assertThat(index.sequence()).isEqualTo(10);
        assertThat(index.bundles()).singleElement().satisfies(entry -> {
            assertThat(entry.pdpId()).isEqualTo("odoo-erp");
            assertThat(entry.url()).endsWith("/bundles/odoo-erp");
        });
    }

    @Test
    @DisplayName("an index signed by an untrusted key is rejected")
    void whenSignedByUntrustedKeyThenRejected() {
        val jws = RealmIndexSigner.sign(sampleIndex(10), untrustedKeys.getPrivate(), "k1");
        assertThatThrownBy(() -> RealmIndexVerifier.verify(jws, signedPolicy, "acme", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("signature");
    }

    @Test
    @DisplayName("an unresolvable key id is rejected as a RealmIndexException, not a leaking exception type")
    void whenKeyIdNotTrustedForRealmThenRejectedAsRealmIndexException() {
        val catalogue = BundleSecurityPolicy.builder().withKeyCatalogue(Map.of("k1", trustedKeys.getPublic()))
                .withTenantTrust(Map.of("acme", Set.of("k1"))).build();
        val jws       = RealmIndexSigner.sign(sampleIndex(10), trustedKeys.getPrivate(), "k9");
        assertThatThrownBy(() -> RealmIndexVerifier.verify(jws, catalogue, "acme", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("key");
    }

    @Test
    @DisplayName("a tampered payload is rejected")
    void whenPayloadTamperedThenRejected() {
        val tampered = flipPayloadCharacter(RealmIndexSigner.sign(sampleIndex(10), trustedKeys.getPrivate(), "k1"));
        assertThatThrownBy(() -> RealmIndexVerifier.verify(tampered, signedPolicy, "acme", 5))
                .isInstanceOf(RealmIndexException.class);
    }

    @Test
    @DisplayName("an index for a different realm is rejected")
    void whenWrongRealmThenRejected() {
        val jws = RealmIndexSigner.sign(sampleIndex(10), trustedKeys.getPrivate(), "k1");
        assertThatThrownBy(() -> RealmIndexVerifier.verify(jws, signedPolicy, "other-realm", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("realm");
    }

    @ParameterizedTest(name = "lastAccepted={0}")
    @ValueSource(longs = { 10, 11 })
    @DisplayName("an index whose sequence is not strictly newer is rejected")
    void whenStaleSequenceThenRejected(long lastAccepted) {
        val jws = RealmIndexSigner.sign(sampleIndex(10), trustedKeys.getPrivate(), "k1");
        assertThatThrownBy(() -> RealmIndexVerifier.verify(jws, signedPolicy, "acme", lastAccepted))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("sequence");
    }

    @Test
    @DisplayName("a non-EdDSA JWS is rejected (algorithm pinning)")
    void whenNotEdDsaThenRejected() throws Exception {
        val mac = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType(RealmIndexSigner.TYPE)).build(),
                new Payload(MAPPER.writeValueAsString(sampleIndex(10))));
        mac.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
        val compact = mac.serialize();
        assertThatThrownBy(() -> RealmIndexVerifier.verify(compact, signedPolicy, "acme", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("EdDSA");
    }

    @Test
    @DisplayName("a JWS with the wrong type is rejected")
    void whenWrongTypeThenRejected() throws Exception {
        val jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("k1")
                .type(new JOSEObjectType("something-else")).build(),
                new Payload(MAPPER.writeValueAsString(sampleIndex(10))));
        jws.sign(new EdDsaJwsSigner(trustedKeys.getPrivate()));
        val compact = jws.serialize();
        assertThatThrownBy(() -> RealmIndexVerifier.verify(compact, signedPolicy, "acme", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("type");
    }

    @Test
    @DisplayName("development mode skips the signature but still checks realm and sequence")
    void whenDevelopmentModeThenSignatureSkippedButRealmEnforced() {
        val jws   = RealmIndexSigner.sign(sampleIndex(10), untrustedKeys.getPrivate(), "k1");
        val index = RealmIndexVerifier.verify(jws, developmentPolicy, "acme", 5);
        assertThat(index.sequence()).isEqualTo(10);
        val wrongRealm = RealmIndexSigner.sign(sampleIndex(10), untrustedKeys.getPrivate(), "k1");
        assertThatThrownBy(() -> RealmIndexVerifier.verify(wrongRealm, developmentPolicy, "other-realm", 5))
                .isInstanceOf(RealmIndexException.class).hasMessageContaining("realm");
    }

    private static String flipPayloadCharacter(String compactJws) {
        val parts       = compactJws.split("\\.");
        val payload     = parts[1];
        val index       = payload.length() / 2;
        val replacement = payload.charAt(index) == 'A' ? 'B' : 'A';
        parts[1] = payload.substring(0, index) + replacement + payload.substring(index + 1);
        return parts[0] + "." + parts[1] + "." + parts[2];
    }
}
