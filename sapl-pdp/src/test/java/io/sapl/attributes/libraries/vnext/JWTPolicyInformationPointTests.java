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
package io.sapl.attributes.libraries.vnext;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.libraries.vnext.util.Base64DataUtil;
import io.sapl.attributes.libraries.vnext.util.DispatchMode;
import io.sapl.attributes.libraries.vnext.util.JWTTestUtility;
import io.sapl.attributes.libraries.vnext.util.JsonTestUtility;
import io.sapl.attributes.libraries.vnext.util.KeyTestUtility;
import io.sapl.attributes.libraries.vnext.util.MutableClock;
import io.sapl.attributes.libraries.vnext.util.StreamAssertions;
import io.sapl.attributes.libraries.vnext.util.TestMockServerDispatcher;
import io.sapl.attributes.libraries.vnext.util.TestTimeScheduler;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JWTPolicyInformationPoint (vnext)")
class JWTPolicyInformationPointTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant    NOW    = Instant.parse("2025-06-15T12:00:00Z");

    private static KeyPair                  keyPair;
    private static KeyPair                  keyPair2;
    private static String                   kid;
    private static String                   kid2;
    private static MockWebServer            server;
    private static TestMockServerDispatcher dispatcher;

    private MutableClock              clock;
    private TestTimeScheduler         scheduler;
    private JWTKeyProvider            provider;
    private JWTPolicyInformationPoint sut;

    @BeforeAll
    static void preSetup() throws IOException, NoSuchAlgorithmException {
        Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.OFF);
        keyPair    = Base64DataUtil.generateRSAKeyPair();
        keyPair2   = Base64DataUtil.generateRSAKeyPair();
        kid        = KeyTestUtility.kid(keyPair);
        kid2       = KeyTestUtility.kid(keyPair2);
        server     = KeyTestUtility.testServer(keyPair);
        dispatcher = (TestMockServerDispatcher) server.getDispatcher();
        server.start();
    }

    @AfterAll
    static void teardown() throws IOException {
        server.close();
    }

    @BeforeEach
    void setup() {
        clock     = new MutableClock(NOW);
        scheduler = new TestTimeScheduler(NOW);
        provider  = new JWTKeyProvider(HttpClient.newHttpClient(), clock);
        sut       = new JWTPolicyInformationPoint(provider, clock, scheduler);
    }

    private static String validity(Value v) {
        return ((ObjectValue) v).get("validity").toString().replace("\"", "");
    }

    private static Value field(Value v, String key) {
        return ((ObjectValue) v).get(key);
    }

    @Nested
    @DisplayName("when token is missing from secrets")
    class MissingTokenTests {

        @Test
        @DisplayName("no token under jwt key emits MISSING_TOKEN")
        void whenNoTokenInSecretsThenMissingToken() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of());

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "validity")).isEqualTo(Value.of("MISSING_TOKEN"));
                    assertThat(field(v, "valid")).isEqualTo(Value.FALSE);
                });
            }
        }

        @Test
        @DisplayName("non-text token value emits MISSING_TOKEN")
        void whenTokenValueIsNotTextThenMissingToken() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", Value.of(42)));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "validity")).isEqualTo(Value.of("MISSING_TOKEN")));
            }
        }
    }

    @Nested
    @DisplayName("when token is malformed")
    class MalformedTokenTests {

        @Test
        @DisplayName("non-JWT string emits MALFORMED")
        void whenMalformedTokenThenMalformed() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null),
                    Map.of("jwt", Value.of("MALFORMED TOKEN")));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "validity")).isEqualTo(Value.of("MALFORMED"));
                    assertThat(field(v, "valid")).isEqualTo(Value.FALSE);
                });
            }
        }

        @Test
        @DisplayName("numeric-string token emits MALFORMED")
        void whenNumericTokenValueInSecretsThenMalformed() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", Value.of("50000")));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "validity")).isEqualTo(Value.of("MALFORMED")));
            }
        }
    }

    @Nested
    @DisplayName("with invalid key")
    class InvalidKeyTests {

        @Test
        @DisplayName("cached invalid key emits UNTRUSTED")
        void whenInvalidKeyCachedThenUntrusted() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, KeyTestUtility.generateInvalidRSAPublicKey());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "validity")).isEqualTo(Value.of("UNTRUSTED"));
                    assertThat(field(v, "valid")).isEqualTo(Value.FALSE);
                });
            }
        }
    }

    @Nested
    @DisplayName("with whitelist keys")
    class WhitelistTests {

        @Test
        @DisplayName("whitelist with empty entry for the kid emits UNTRUSTED")
        void whenWhitelistHasEmptyEntryThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, null, kid2, keyPair2),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "validity")).isEqualTo(Value.of("UNTRUSTED")));
            }
        }

        @Test
        @DisplayName("whitelist entry containing a bogus key emits UNTRUSTED")
        void whenWhitelistHasBogusEntryThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid2).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair2);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, null),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "validity")).isEqualTo(Value.of("UNTRUSTED")));
            }
        }

        @Test
        @DisplayName("multiple keys in whitelist resolve each kid correctly")
        void whenWhitelistHasMultipleKeysThenCorrectValidation()
                throws NoSuchAlgorithmException, IOException, JOSEException {
            val keyPair3 = Base64DataUtil.generateRSAKeyPair();

            val accessCtx1 = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2),
                    Map.of("jwt",
                            JWTTestUtility.buildAndSignJwt(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
                                    new JWTClaimsSet.Builder().build(), keyPair)));
            val accessCtx2 = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2),
                    Map.of("jwt",
                            JWTTestUtility.buildAndSignJwt(
                                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid2).build(),
                                    new JWTClaimsSet.Builder().build(), keyPair2)));
            val accessCtx3 = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2),
                    Map.of("jwt", JWTTestUtility.buildAndSignJwt(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KeyTestUtility.kid(keyPair3)).build(),
                            new JWTClaimsSet.Builder().build(), keyPair3)));

            try (val s1 = sut.token(accessCtx1); val s2 = sut.token(accessCtx2); val s3 = sut.token(accessCtx3)) {
                StreamAssertions.assertThat(s1).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
                StreamAssertions.assertThat(s2).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
                StreamAssertions.assertThat(s3).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }
    }

    @Nested
    @DisplayName("with environment configuration")
    class EnvironmentTests {

        @Test
        @DisplayName("empty variables emits UNTRUSTED")
        void whenEmptyVariablesThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(Map.of(), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }

        @Test
        @DisplayName("missing key-server config emits UNTRUSTED")
        void whenMissingServerConfigThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(Map.of("jwt", Value.EMPTY_OBJECT), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }

        @Test
        @DisplayName("server returning the wrong key emits UNTRUSTED")
        void whenWrongKeyFromServerThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.WRONG);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }

        @Test
        @DisplayName("invalid caching TTL configuration emits UNTRUSTED")
        void whenInvalidCachingTtlThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val jwtNode   = MAPPER.createObjectNode().set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY,
                    JsonTestUtility.serverNode(server, null, "invalid TTL format"));
            val variables = Map.of("jwt", ValueJsonMarshaller.fromJsonNode(jwtNode));
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }
    }

    @Nested
    @DisplayName("with header claims validation")
    class HeaderClaimsTests {

        @Test
        @DisplayName("missing kid emits INCOMPLETE")
        void whenMissingKeyIdThenIncomplete() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("INCOMPLETE"));
            }
        }

        @Test
        @DisplayName("blank kid emits INCOMPLETE")
        void whenEmptyKeyIdThenIncomplete() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("").build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("INCOMPLETE"));
            }
        }

        @Test
        @DisplayName("critical header parameter emits INCOMPATIBLE")
        void whenCriticalHeaderParamThenIncompatible() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).criticalParams(Set.of("critparam"))
                    .build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("INCOMPATIBLE"));
            }
        }
    }

    @Nested
    @DisplayName("with signature validation")
    class SignatureTests {

        @Test
        @DisplayName("tampered payload emits UNTRUSTED")
        void whenTamperedPayloadThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header         = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims         = new JWTClaimsSet.Builder().build();
            val tamperedClaims = new JWTClaimsSet.Builder().jwtID("").build();
            val originalJwt    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val source         = JWTTestUtility.replacePayload(originalJwt, tamperedClaims);
            val accessCtx      = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }

        @Test
        @DisplayName("PS512-signed token validates with the correct key")
        void whenPS512AlgorithmThenValidWithCorrectKey() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "valid")).isEqualTo(Value.TRUE);
                    assertThat(validity(v)).isEqualTo("VALID");
                });
            }
        }
    }

    @Nested
    @DisplayName("with time-based validation")
    class TimeClaimsTests {

        @Test
        @DisplayName("nbf after exp emits NEVER_VALID")
        void whenNbfAfterExpThenNeverValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(2)))
                    .notBeforeTime(Date.from(NOW.plusSeconds(2))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("NEVER_VALID"));
            }
        }

        @Test
        @DisplayName("exp in the past emits EXPIRED")
        void whenExpiredThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(2))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("EXPIRED"));
            }
        }

        @Test
        @DisplayName("exp in future emits VALID then EXPIRED at the boundary")
        void whenExpiresInFutureThenValidThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val expiry = NOW.plusSeconds(5);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(Date.from(expiry)).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
                clock.setInstant(expiry);
                scheduler.advanceTo(expiry);
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("EXPIRED"));
            }
        }

        @Test
        @DisplayName("nbf in the future emits IMMATURE then VALID at the boundary")
        void whenImmatureThenImmatureThenValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val nbf    = NOW.plusSeconds(5);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().notBeforeTime(Date.from(nbf)).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("IMMATURE"));
                clock.setInstant(nbf);
                scheduler.advanceTo(nbf);
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
            }
        }

        @Test
        @DisplayName("nbf in past with no exp emits VALID")
        void whenNbfBeforeNowThenValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().notBeforeTime(Date.from(NOW.minusSeconds(2))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(validity(v)).isEqualTo("VALID");
                    assertThat(field(v, "valid")).isEqualTo(Value.TRUE);
                });
            }
        }

        @Test
        @DisplayName("nbf and exp in future emits IMMATURE -> VALID -> EXPIRED")
        void whenImmatureWithExpirationThenImmatureThenValidThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val nbf    = NOW.plusSeconds(2);
            val exp    = NOW.plusSeconds(4);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().notBeforeTime(Date.from(nbf)).expirationTime(Date.from(exp))
                    .build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("IMMATURE"));
                clock.setInstant(nbf);
                scheduler.advanceTo(nbf);
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
                clock.setInstant(exp);
                scheduler.advanceTo(exp);
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("EXPIRED"));
            }
        }
    }

    @Nested
    @DisplayName("with ObjectValue structure")
    class ObjectValueStructureTests {

        @Test
        @DisplayName("valid token contains header, payload, valid, validity")
        void whenValidTokenThenObjectValueContainsAllFields() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().subject("user123").build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    val obj = (ObjectValue) v;
                    assertThat(obj.containsKey("header")).isTrue();
                    assertThat(obj.containsKey("payload")).isTrue();
                    assertThat(obj.containsKey("valid")).isTrue();
                    assertThat(obj.containsKey("validity")).isTrue();
                    assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
                    assertThat(validity(v)).isEqualTo("VALID");
                    val payload = (ObjectValue) obj.get("payload");
                    assertThat(payload.get("sub")).isEqualTo(Value.of("user123"));
                    val headerVal = (ObjectValue) obj.get("header");
                    assertThat(headerVal.get("kid")).isEqualTo(Value.of(kid));
                    assertThat(headerVal.get("alg")).isEqualTo(Value.of("RS256"));
                });
            }
        }

        @Test
        @DisplayName("epoch time claims are converted to ISO-8601 in the payload")
        void whenValidTokenWithTimeClaimsThenEpochConvertedToIso() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val nbf    = new Date(1635251415000L);
            val exp    = new Date(1635251715000L);
            val iat    = new Date(1635251415000L);
            val claims = new JWTClaimsSet.Builder().notBeforeTime(nbf).expirationTime(exp).issueTime(iat).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    val payload = (ObjectValue) ((ObjectValue) v).get("payload");
                    assertThat(payload.get("nbf")).isEqualTo(Value.of("2021-10-26T12:30:15Z"));
                    assertThat(payload.get("exp")).isEqualTo(Value.of("2021-10-26T12:35:15Z"));
                    assertThat(payload.get("iat")).isEqualTo(Value.of("2021-10-26T12:30:15Z"));
                });
            }
        }

        @Test
        @DisplayName("missing token yields empty header and payload")
        void whenMissingTokenThenEmptyHeaderAndPayload() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of());

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "header")).isEqualTo(Value.EMPTY_OBJECT);
                    assertThat(field(v, "payload")).isEqualTo(Value.EMPTY_OBJECT);
                    assertThat(validity(v)).isEqualTo("MISSING_TOKEN");
                });
            }
        }
    }

    @Nested
    @DisplayName("with custom secrets key")
    class CustomSecretsKeyTests {

        @Test
        @DisplayName("argument-supplied secrets key reads from that key in subscription secrets")
        void whenCustomSecretsKeyThenReadsFromCorrectKey() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("myToken", source));

            try (val stream = sut.token(accessCtx, Value.of("myToken"))) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "valid")).isEqualTo(Value.TRUE));
            }
        }

        @Test
        @DisplayName("config-supplied secrets key reads from that key in subscription secrets")
        void whenCustomSecretsKeyFromConfigThenReadsFromCorrectKey() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val jwtConfigNode = MAPPER.createObjectNode();
            jwtConfigNode.put(JWTPolicyInformationPoint.SECRETS_KEY, "accessToken");
            jwtConfigNode.set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY,
                    JsonTestUtility.serverNode(server, null, null));
            val variables = Map.of("jwt", (Value) ValueJsonMarshaller.fromJsonNode(jwtConfigNode));
            val accessCtx = ctx(variables, Map.of("accessToken", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "valid")).isEqualTo(Value.TRUE));
            }
        }
    }

    @Nested
    @DisplayName("with HMAC keys")
    class HmacKeyTests {

        @Test
        @DisplayName("HS256 signed token with whitelisted key emits VALID")
        void whenValidHmacTokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(32);
            val hmacKid   = "hmac-key-1";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "valid")).isEqualTo(Value.TRUE);
                    assertThat(validity(v)).isEqualTo("VALID");
                });
            }
        }

        @Test
        @DisplayName("HS256 token signed with one key, validated against another emits UNTRUSTED")
        void whenWrongHmacKeyThenUntrusted() throws JOSEException {
            val signingKey = Base64DataUtil.generateHmacKey(32);
            val wrongKey   = Base64DataUtil.generateHmacKey(32);
            val hmacKid    = "hmac-key-1";
            val header     = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(hmacKid).build();
            val claims     = new JWTClaimsSet.Builder().subject("user").build();
            val source     = JWTTestUtility.buildAndSignHmacJwt(header, claims, signingKey);
            val encoded    = JWTTestUtility.encodeSecretKey(wrongKey);
            val accessCtx  = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("UNTRUSTED"));
            }
        }

        @Test
        @DisplayName("HS384 signed token with correct key emits VALID")
        void whenHs384TokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(48);
            val hmacKid   = "hmac-key-384";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS384).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "valid")).isEqualTo(Value.TRUE));
            }
        }

        @Test
        @DisplayName("HS512 signed token with correct key emits VALID")
        void whenHs512TokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(64);
            val hmacKid   = "hmac-key-512";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS512).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(field(v, "valid")).isEqualTo(Value.TRUE));
            }
        }
    }

    @Nested
    @DisplayName("with EC keys")
    class EcKeyTests {

        @Test
        @DisplayName("ES256 signed token with whitelisted EC key emits VALID")
        void whenValidEcTokenThenValid() throws JOSEException, NoSuchAlgorithmException, IOException {
            val ecKeyPair = Base64DataUtil.generateECKeyPair();
            val ecKid     = KeyTestUtility.kid(ecKeyPair);
            val header    = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, ecKeyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(ecKid, ecKeyPair, kid, keyPair),
                    Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    assertThat(field(v, "valid")).isEqualTo(Value.TRUE);
                    assertThat(validity(v)).isEqualTo("VALID");
                });
            }
        }
    }

    @Nested
    @DisplayName("with clock skew tolerance")
    class ClockSkewTests {

        @Test
        @DisplayName("expired within skew window stays VALID")
        void whenExpiredWithinSkewThenValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(30))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
            }
        }

        @Test
        @DisplayName("expired beyond skew window emits EXPIRED")
        void whenExpiredBeyondSkewThenExpired() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(90))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("EXPIRED"));
            }
        }

        @Test
        @DisplayName("immature within skew window stays VALID")
        void whenImmatureWithinSkewThenValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().notBeforeTime(Date.from(NOW.plusSeconds(30))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
            }
        }

        @Test
        @DisplayName("zero skew yields exact comparison")
        void whenClockSkewZeroThenExact() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(5))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 0));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("EXPIRED"));
            }
        }
    }

    @Nested
    @DisplayName("with max token lifetime")
    class MaxTokenLifetimeTests {

        @Test
        @DisplayName("lifetime exceeding max emits NEVER_VALID")
        void whenLifetimeExceedsMaxThenNeverValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().issueTime(Date.from(NOW))
                    .expirationTime(Date.from(NOW.plusSeconds(86_401))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.MAX_TOKEN_LIFETIME_SECONDS_KEY, 86_400));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("NEVER_VALID"));
            }
        }

        @Test
        @DisplayName("lifetime equal to max is not rejected")
        void whenLifetimeEqualsMaxThenNotRejected() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().issueTime(Date.from(NOW))
                    .expirationTime(Date.from(NOW.plusSeconds(86_400))).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.MAX_TOKEN_LIFETIME_SECONDS_KEY, 86_400));
            val accessCtx = ctx(variables, Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream)
                        .awaitsNext(v -> assertThat(validity(v)).isNotEqualTo("NEVER_VALID"));
            }
        }

        @Test
        @DisplayName("no max configured: extreme exp accepted")
        void whenNoMaxConfiguredThenExtremeExpAccepted() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.plusSeconds(999_999))).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(validity(v)).isEqualTo("VALID"));
            }
        }
    }

    @Nested
    @DisplayName("with epoch bounds validation")
    class EpochBoundsTests {

        @Test
        @DisplayName("extreme epoch keeps the raw numeric value in the payload")
        void whenExtremeEpochThenRawNumericValue() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(new Date(999_999_999_999_999L)).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    val payload = (ObjectValue) ((ObjectValue) v).get("payload");
                    val expVal  = payload.get("exp");
                    assertThat(expVal).isNotNull();
                    assertThat(expVal.toString()).doesNotContain("T");
                });
            }
        }

        @Test
        @DisplayName("reasonable epoch is converted to ISO string")
        void whenReasonableEpochThenIsoString() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(new Date(1635251715000L)).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));

            try (val stream = sut.token(accessCtx)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    val payload = (ObjectValue) ((ObjectValue) v).get("payload");
                    val expVal  = payload.get("exp");
                    assertThat(expVal).isNotNull();
                    assertThat(expVal.toString()).contains("T");
                });
            }
        }
    }

    @Nested
    @DisplayName("store registration")
    class StoreRegistration {

        @Test
        @Disabled("TODO: enable when AttributeMethodSignatureProcessor accepts Stream<Value>")
        @DisplayName("loads under the jwt namespace without errors")
        void whenLoadedIntoStoreThenRegistersUnderJwtNamespace() {
            // Mirror of the original library-load test. Cannot run until the
            // signature processor accepts Stream<Value> return types.
        }
    }

    static AttributeAccessContext ctx(Map<String, Value> variables, Map<String, Value> subscriptionSecrets) {
        val varBuilder     = ObjectValue.builder();
        val secretsBuilder = ObjectValue.builder();
        variables.forEach(varBuilder::put);
        subscriptionSecrets.forEach(secretsBuilder::put);
        return new AttributeAccessContext(varBuilder.build(), Value.EMPTY_OBJECT, secretsBuilder.build());
    }
}
