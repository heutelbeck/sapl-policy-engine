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
package io.sapl.attributes.libraries;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.api.test.stream.TestTimeScheduler;
import lombok.val;
import okhttp3.Protocol;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static io.sapl.attributes.libraries.util.Base64DataUtil.generateRSAKeyPair;
import static io.sapl.attributes.libraries.util.JWTTestUtility.buildAndSignJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("JWT key-server TLS trust")
class JWTPemTrustTests {
    private static final Instant            NOW    = Instant.parse("2026-01-01T00:00:00Z");
    private static final KeyPair            SIGNER = generateRSAKeyPair();
    private static final HeldCertificate    ROOT   = new HeldCertificate.Builder().certificateAuthority(1).build();
    private static final HeldCertificate    OTHER  = new HeldCertificate.Builder().certificateAuthority(1).build();
    private final MockWebServer             server = new MockWebServer();
    private final HttpClient                client = HttpClient.newHttpClient();
    private final JWTPolicyInformationPoint pip;

    JWTPemTrustTests() {
        val clock = Clock.fixed(NOW, ZoneOffset.UTC);
        pip = new JWTPolicyInformationPoint(new JWTKeyProvider(client, clock), clock, new TestTimeScheduler(NOW));
    }

    @AfterEach
    void close() throws IOException {
        server.close();
        client.close();
    }

    @Test
    @DisplayName("changing or removing the CA isolates cached signing keys for the same URI and key ID")
    void whenTrustChangesThenCachedSigningKeyRequiresMatchingTrust() throws Exception {
        val uri = startTls();
        val pem = Value.of(ROOT.certificatePem());
        assertValidity(uri, profile(pem), "VALID");
        assertValidity(uri, profile(pem), "VALID");
        assertValidity(uri, profile(Value.of(OTHER.certificatePem())), "UNTRUSTED");
        assertValidity(uri, Value.EMPTY_OBJECT, "UNTRUSTED");
        assertValidity(uri, profile(pem), "VALID");
        assertThat(server.getRequestCount()).isOne();
    }

    @Test
    @DisplayName("custom key-server trust requires HTTPS even when plaintext is enabled")
    void whenPlaintextIsEnabledWithCustomCaThenKeyIsUnavailable() throws Exception {
        server.start();
        enqueueKey();
        assertValidity("http://localhost:" + server.getPort() + "/keys/{kid}", profile(Value.of(ROOT.certificatePem())),
                "UNTRUSTED");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("enabling platform roots also selects a new signing-key cache")
    void whenDefaultTrustRootsAreAddedThenSigningKeyIsFetchedAgain() throws Exception {
        val uri = startTls();
        val pem = Value.of(ROOT.certificatePem());
        enqueueKey();
        assertValidity(uri, profile(pem), "VALID");
        assertValidity(uri,
                ObjectValue.builder().put("trustedCaPem", pem).put("includeDefaultTrustRoots", Value.TRUE).build(),
                "VALID");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("private trust still authenticates the configured host")
    void whenHostnameDiffersThenKeyIsUnavailable() throws Exception {
        val uri = startTls().replace("localhost", "127.0.0.1");
        assertValidity(uri, profile(Value.of(ROOT.certificatePem())), "UNTRUSTED");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("subscription settings and secret-supplied roots cannot override the configured key server")
    void whenSecretsSupplyTrustThenIgnoreIt() throws Exception {
        val uri      = startTls();
        val trusted  = profile(Value.of(ROOT.certificatePem()));
        val proposed = ObjectValue.builder().put("tlsTrustProfiles", registry(trusted))
                .put("jwt",
                        ObjectValue.builder().put("publicKeyServer",
                                ObjectValue.builder().putAll(trusted).put("trustProfile", Value.of("private")).build())
                                .build())
                .build();
        val config   = ObjectValue.builder().put("uri", Value.of(uri)).put("trustProfile", Value.of("private")).build();
        assertValidity(config, registry(profile(Value.of(OTHER.certificatePem()))), proposed, proposed, "UNTRUSTED");
        val defaultConfig = ObjectValue.builder().put("uri", Value.of(uri)).build();
        assertValidity(defaultConfig, registry(trusted), proposed, proposed, "UNTRUSTED");
        assertThat(server.getRequestCount()).isZero();
    }

    static Stream<Arguments> invalidReferences() {
        return Stream.of(arguments("missing profile", Value.of("missing"), Value.EMPTY_OBJECT),
                arguments("non-text reference", Value.of(5), Value.EMPTY_OBJECT),
                arguments("invalid PEM", Value.of("private"), registry(profile(Value.of("malformed")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidReferences")
    @DisplayName("a broken trust reference yields an untrusted token")
    void whenConfiguredTrustIsInvalidThenKeyIsUnavailable(String description, Value reference, ObjectValue profiles)
            throws Exception {
        server.start();
        enqueueKey();
        val endpoint = ObjectValue.builder()
                .put("uri", Value.of("http://localhost:" + server.getPort() + "/keys/{kid}"))
                .put("trustProfile", reference).put("allowInsecureHttp", Value.TRUE).build();
        assertValidity(endpoint, profiles, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, "UNTRUSTED");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("a key ID cannot select the key server's host")
    void whenUriTemplatePlacesKeyIdInAuthorityThenKeyIsUnavailable() throws Exception {
        server.start();
        enqueueKey();
        assertValidity("http://{kid}:" + server.getPort() + "/keys", Value.EMPTY_OBJECT, "UNTRUSTED");
        assertThat(server.getRequestCount()).isZero();
    }

    private String startTls() throws IOException {
        val leaf = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").signedBy(ROOT).build();
        val tls  = new HandshakeCertificates.Builder().heldCertificate(leaf, ROOT.certificate()).build();
        server.setProtocols(List.of(Protocol.HTTP_1_1));
        server.useHttps(tls.sslSocketFactory(), false);
        server.start();
        enqueueKey();
        return "https://localhost:" + server.getPort() + "/keys/{kid}";
    }

    private void enqueueKey() {
        server.enqueue(new MockResponse().setBody(Base64.getEncoder().encodeToString(SIGNER.getPublic().getEncoded())));
    }

    private void assertValidity(String uri, ObjectValue profile, String expected) throws JOSEException {
        val endpoint = ObjectValue.builder().put("uri", Value.of(uri)).put("allowInsecureHttp", Value.TRUE);
        if (!profile.isEmpty()) {
            endpoint.put("trustProfile", Value.of("private"));
        }
        assertValidity(endpoint.build(), registry(profile), Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, expected);
    }

    private void assertValidity(ObjectValue keyServer, ObjectValue profiles, ObjectValue operator,
            ObjectValue subscriptionSecrets, String expected) throws JOSEException {
        val variables    = ObjectValue.builder().put("tlsTrustProfiles", profiles).put("jwt",
                ObjectValue.builder().put("publicKeyServer", keyServer).put("secretsKey", Value.of("token")).build())
                .build();
        val token        = buildAndSignJwt(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("localhost").build(),
                new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.plusSeconds(300))).build(), SIGNER);
        val subscription = ObjectValue.builder().putAll(subscriptionSecrets).put("token", token).build();
        val ctx          = new AttributeAccessContext(variables, operator, subscription);
        try (val stream = pip.token(ctx)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                    .awaitsNext(v -> assertThat(v).isInstanceOfSatisfying(ObjectValue.class,
                            obj -> assertThat(obj.get("validity")).isEqualTo(Value.of(expected))));
        }
    }

    private static ObjectValue profile(Value pem) {
        return ObjectValue.builder().put("trustedCaPem", pem).build();
    }

    private static ObjectValue registry(ObjectValue profile) {
        return profile.isEmpty() ? Value.EMPTY_OBJECT : ObjectValue.builder().put("private", profile).build();
    }
}
