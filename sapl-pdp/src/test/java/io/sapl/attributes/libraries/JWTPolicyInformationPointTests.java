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

import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.util.Base64DataUtil;
import io.sapl.attributes.libraries.util.DispatchMode;
import io.sapl.attributes.libraries.util.JWTTestUtility;
import io.sapl.attributes.libraries.util.JsonTestUtility;
import io.sapl.attributes.libraries.util.KeyTestUtility;
import io.sapl.attributes.libraries.util.TestMockServerDispatcher;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWTPolicyInformationPoint")
class JWTPolicyInformationPointTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static KeyPair                  keyPair;
    private static KeyPair                  keyPair2;
    private static String                   kid;
    private static String                   kid2;
    private static WebClient.Builder        builder;
    private static MockWebServer            server;
    private static TestMockServerDispatcher dispatcher;
    private JWTPolicyInformationPoint       jwtPolicyInformationPoint;
    private JWTKeyProvider                  provider;

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
        builder = WebClient.builder();
    }

    @AfterAll
    static void teardown() throws IOException {
        server.close();
    }

    @BeforeEach
    void setup() {
        provider                  = new JWTKeyProvider(builder);
        jwtPolicyInformationPoint = new JWTPolicyInformationPoint(provider);
    }

    @Test
    void whenBrokerLoadsLibraryThenJwtLibraryIsAvailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new JWTPolicyInformationPoint(provider);

        broker.loadPolicyInformationPointLibrary(pip);

        assertThat(broker.getLoadedLibraryNames()).contains("jwt");
    }

    @Test
    void whenLoadLibraryWithoutAnnotationThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        class NotAnnotated {
            @SuppressWarnings("unused")
            public Value someAttribute() {
                return Value.of("test");
            }
        }

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NotAnnotated()))
                .hasMessageContaining("must be annotated with @PolicyInformationPoint");
    }

    @Test
    void whenLoadDuplicateLibraryThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new JWTPolicyInformationPoint(provider);

        broker.loadPolicyInformationPointLibrary(pip);

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new JWTPolicyInformationPoint(provider)))
                .hasMessageContaining("Library already loaded: jwt");
    }

    @Nested
    @DisplayName("when token is missing from secrets")
    class MissingTokenTests {

        @Test
        void whenNoTokenInSecretsThenMissingToken() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of());
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("MISSING_TOKEN"));
                assertThat(obj.get("valid")).isEqualTo(Value.FALSE);
            }).verifyComplete();
        }

        @Test
        void whenTokenValueIsNotTextThenMissingToken() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", Value.of(42)));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("MISSING_TOKEN"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("when token is malformed")
    class MalformedTokenTests {

        @Test
        void whenMalformedTokenThenMalformed() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null),
                    Map.of("jwt", Value.of("MALFORMED TOKEN")));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("MALFORMED"));
                assertThat(obj.get("valid")).isEqualTo(Value.FALSE);
            }).verifyComplete();
        }

        @Test
        void whenNumericTokenValueInSecretsThenMalformed() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", Value.of("50000")));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("MALFORMED"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with invalid key")
    class InvalidKeyTests {

        @Test
        void whenInvalidKeyCachedThenUntrusted() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, KeyTestUtility.generateInvalidRSAPublicKey());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
                assertThat(obj.get("valid")).isEqualTo(Value.FALSE);
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with whitelist keys")
    class WhitelistTests {

        @Test
        void whenWhitelistHasEmptyEntryThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, null, kid2, keyPair2),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenWhitelistHasBogusEntryThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid2).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair2);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, null),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenWhitelistHasMultipleKeysThenCorrectValidation()
                throws NoSuchAlgorithmException, IOException, JOSEException {
            val keyPairs   = new KeyPair[] { keyPair, keyPair2, Base64DataUtil.generateRSAKeyPair() };
            val claims     = new JWTClaimsSet.Builder().build();
            val validities = new ArrayList<Mono<Value>>();
            for (int trial = 0; trial < 3; trial++) {
                val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KeyTestUtility.kid(keyPairs[trial]))
                        .build();
                val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPairs[trial]);
                val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2),
                        Map.of("jwt", source));
                validities.add(
                        jwtPolicyInformationPoint.token(accessCtx).last().map(v -> ((ObjectValue) v).get("validity")));
            }
            val flux = Flux.concat(validities);
            StepVerifier.create(flux).expectNext(Value.of("VALID")).expectNext(Value.of("VALID"))
                    .expectNext(Value.of("UNTRUSTED")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with environment configuration")
    class EnvironmentTests {

        @Test
        void whenEmptyVariablesThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(Map.of(), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenMissingServerConfigThenUntrusted() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(Map.of("jwt", Value.EMPTY_OBJECT), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenWrongKeyFromServerThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.WRONG);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenInvalidCachingTtlThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val jwtNode   = MAPPER.createObjectNode().set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY,
                    JsonTestUtility.serverNode(server, null, "invalid TTL format"));
            val variables = Map.of("jwt", ValueJsonMarshaller.fromJsonNode(jwtNode));
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with header claims validation")
    class HeaderClaimsTests {

        @Test
        void whenMissingKeyIdThenIncomplete() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("INCOMPLETE"));
            }).verifyComplete();
        }

        @Test
        void whenEmptyKeyIdThenIncomplete() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("").build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("INCOMPLETE"));
            }).verifyComplete();
        }

        @Test
        void whenCriticalHeaderParamThenIncompatible() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).criticalParams(Set.of("critparam"))
                    .build();
            val claims    = new JWTClaimsSet.Builder().build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("INCOMPATIBLE"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with signature validation")
    class SignatureTests {

        @Test
        void whenTamperedPayloadThenUntrusted() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header         = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims         = new JWTClaimsSet.Builder().build();
            val tamperedClaims = new JWTClaimsSet.Builder().jwtID("").build();
            val originalJWT    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val source         = JWTTestUtility.replacePayload(originalJWT, tamperedClaims);
            val accessCtx      = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux           = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        void whenPS512AlgorithmThenValidWithCorrectKey() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with time-based validation")
    class TimeClaimsTests {

        @Test
        void whenNbfAfterExpThenNeverValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow())
                    .notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("NEVER_VALID"));
            }).verifyComplete();
        }

        @Test
        void whenExpiredThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("EXPIRED"));
            }).verifyComplete();
        }

        @Test
        void whenExpiresInFutureThenValidThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitAfterNow()).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            StepVerifier
                    .withVirtualTime(() -> jwtPolicyInformationPoint.token(accessCtx)
                            .map(v -> ((ObjectValue) v).get("validity")))
                    .expectNext(Value.of("VALID")).thenAwait(JWTTestUtility.twoUnitDuration())
                    .expectNext(Value.of("EXPIRED")).verifyComplete();
        }

        @Test
        void whenImmatureThenImmatureThenValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            StepVerifier
                    .withVirtualTime(() -> jwtPolicyInformationPoint.token(accessCtx)
                            .map(v -> ((ObjectValue) v).get("validity")))
                    .expectNext(Value.of("IMMATURE")).thenAwait(JWTTestUtility.twoUnitDuration())
                    .expectNext(Value.of("VALID")).verifyComplete();
        }

        @Test
        void whenNbfBeforeNowThenValid() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
            }).verifyComplete();
        }

        @Test
        void whenImmatureWithExpirationThenImmatureThenValidThenExpired() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val nbf       = Date.from(Instant.now().plusSeconds(2));
            val exp       = Date.from(Instant.now().plusSeconds(4));
            val claims    = new JWTClaimsSet.Builder().notBeforeTime(nbf).expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            StepVerifier.create(jwtPolicyInformationPoint.token(accessCtx).map(v -> ((ObjectValue) v).get("validity")))
                    .expectNext(Value.of("IMMATURE")).expectNext(Value.of("VALID")).expectNext(Value.of("EXPIRED"))
                    .thenCancel().verify(Duration.ofSeconds(6));
        }
    }

    @Nested
    @DisplayName("with ObjectValue structure")
    class ObjectValueStructureTests {

        @Test
        void whenValidTokenThenObjectValueContainsAllFields() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().subject("user123").build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.containsKey("header")).isTrue();
                assertThat(obj.containsKey("payload")).isTrue();
                assertThat(obj.containsKey("valid")).isTrue();
                assertThat(obj.containsKey("validity")).isTrue();
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
                val payload = (ObjectValue) obj.get("payload");
                assertThat(payload.get("sub")).isEqualTo(Value.of("user123"));
                val headerVal = (ObjectValue) obj.get("header");
                assertThat(headerVal.get("kid")).isEqualTo(Value.of(kid));
                assertThat(headerVal.get("alg")).isEqualTo(Value.of("RS256"));
            }).verifyComplete();
        }

        @Test
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
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj     = (ObjectValue) result;
                val payload = (ObjectValue) obj.get("payload");
                assertThat(payload.get("nbf")).isEqualTo(Value.of("2021-10-26T12:30:15Z"));
                assertThat(payload.get("exp")).isEqualTo(Value.of("2021-10-26T12:35:15Z"));
                assertThat(payload.get("iat")).isEqualTo(Value.of("2021-10-26T12:30:15Z"));
            }).verifyComplete();
        }

        @Test
        void whenMissingTokenThenEmptyHeaderAndPayload() {
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of());
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("header")).isEqualTo(Value.EMPTY_OBJECT);
                assertThat(obj.get("payload")).isEqualTo(Value.EMPTY_OBJECT);
                assertThat(obj.get("validity")).isEqualTo(Value.of("MISSING_TOKEN"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with custom secrets key")
    class CustomSecretsKeyTests {

        @Test
        void whenCustomSecretsKeyThenReadsFromCorrectKey() throws JOSEException {
            dispatcher.setDispatchMode(DispatchMode.TRUE);
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("myToken", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx, Value.of("myToken"));
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
            }).verifyComplete();
        }

        @Test
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
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with HMAC keys")
    class HmacKeyTests {

        @Test
        @DisplayName("when valid HMAC-signed token with whitelist key then VALID")
        void whenValidHmacTokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(32);
            val hmacKid   = "hmac-key-1";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).verifyComplete();
        }

        @Test
        @DisplayName("when wrong HMAC key then UNTRUSTED")
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
            val flux       = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("UNTRUSTED"));
            }).verifyComplete();
        }

        @Test
        @DisplayName("when HS384 token with correct key then VALID")
        void whenHs384TokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(48);
            val hmacKid   = "hmac-key-384";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS384).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
            }).verifyComplete();
        }

        @Test
        @DisplayName("when HS512 token with correct key then VALID")
        void whenHs512TokenThenValid() throws JOSEException {
            val secretKey = Base64DataUtil.generateHmacKey(64);
            val hmacKid   = "hmac-key-512";
            val header    = new JWSHeader.Builder(JWSAlgorithm.HS512).keyID(hmacKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignHmacJwt(header, claims, secretKey);
            val encoded   = JWTTestUtility.encodeSecretKey(secretKey);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariablesForHmac(hmacKid, encoded),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with EC keys")
    class EcKeyTests {

        @Test
        @DisplayName("when valid EC-signed token with whitelist key then VALID")
        void whenValidEcTokenThenValid() throws JOSEException, NoSuchAlgorithmException, IOException {
            val ecKeyPair = Base64DataUtil.generateECKeyPair();
            val ecKid     = KeyTestUtility.kid(ecKeyPair);
            val header    = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKid).build();
            val claims    = new JWTClaimsSet.Builder().subject("user").build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, ecKeyPair);
            val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(ecKid, ecKeyPair, kid, keyPair),
                    Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("valid")).isEqualTo(Value.TRUE);
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with clock skew tolerance")
    class ClockSkewTests {

        @Test
        @DisplayName("when expired within skew window then still VALID")
        void whenExpiredWithinSkewThenValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val exp       = Date.from(Instant.now().minusSeconds(30));
            val claims    = new JWTClaimsSet.Builder().expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).thenCancel().verify(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("when expired beyond skew window then EXPIRED")
        void whenExpiredBeyondSkewThenExpired() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val exp       = Date.from(Instant.now().minusSeconds(90));
            val claims    = new JWTClaimsSet.Builder().expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("EXPIRED"));
            }).verifyComplete();
        }

        @Test
        @DisplayName("when immature within skew window then still VALID")
        void whenImmatureWithinSkewThenValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val nbf       = Date.from(Instant.now().plusSeconds(30));
            val claims    = new JWTClaimsSet.Builder().notBeforeTime(nbf).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 60));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).thenCancel().verify(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("when clock skew is zero then exact comparison")
        void whenClockSkewZeroThenExact() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val exp       = Date.from(Instant.now().minusSeconds(5));
            val claims    = new JWTClaimsSet.Builder().expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.CLOCK_SKEW_SECONDS_KEY, 0));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("EXPIRED"));
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("with max token lifetime")
    class MaxTokenLifetimeTests {

        @Test
        @DisplayName("when token lifetime exceeds max then NEVER_VALID")
        void whenLifetimeExceedsMaxThenNeverValid() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val iat       = Date.from(Instant.now());
            val exp       = Date.from(Instant.now().plusSeconds(86401));
            val claims    = new JWTClaimsSet.Builder().issueTime(iat).expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.MAX_TOKEN_LIFETIME_SECONDS_KEY, 86400));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("NEVER_VALID"));
            }).verifyComplete();
        }

        @Test
        @DisplayName("when token lifetime equals max then not rejected")
        void whenLifetimeEqualsMaxThenNotRejected() throws JOSEException {
            val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val iat       = Date.from(Instant.now());
            val exp       = Date.from(Instant.now().plusSeconds(86400));
            val claims    = new JWTClaimsSet.Builder().issueTime(iat).expirationTime(exp).build();
            val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            val variables = JsonTestUtility.publicKeyWhitelistVariablesWithConfig(kid, keyPair,
                    node -> node.put(JWTPolicyInformationPoint.MAX_TOKEN_LIFETIME_SECONDS_KEY, 86400));
            val accessCtx = ctx(variables, Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isNotEqualTo(Value.of("NEVER_VALID"));
            }).thenCancel().verify(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("when max not configured then extreme exp accepted")
        void whenNoMaxConfiguredThenExtremeExpAccepted() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val exp    = Date.from(Instant.now().plusSeconds(999_999));
            val claims = new JWTClaimsSet.Builder().expirationTime(exp).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj = (ObjectValue) result;
                assertThat(obj.get("validity")).isEqualTo(Value.of("VALID"));
            }).thenCancel().verify(Duration.ofSeconds(2));
        }
    }

    @Nested
    @DisplayName("with epoch bounds validation")
    class EpochBoundsTests {

        @Test
        @DisplayName("when extreme epoch then payload keeps raw numeric value")
        void whenExtremeEpochThenRawNumericValue() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val claims = new JWTClaimsSet.Builder().expirationTime(new Date(999_999_999_999_999L)).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj     = (ObjectValue) result;
                val payload = (ObjectValue) obj.get("payload");
                val expVal  = payload.get("exp");
                assertThat(expVal).isNotNull();
                assertThat(expVal.toString()).doesNotContain("T");
            }).thenCancel().verify(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("when reasonable epoch then payload has ISO string")
        void whenReasonableEpochThenIsoString() throws JOSEException {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            val exp    = new Date(1635251715000L);
            val claims = new JWTClaimsSet.Builder().expirationTime(exp).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
            provider.cache(kid, keyPair.getPublic());
            val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null), Map.of("jwt", source));
            val flux      = jwtPolicyInformationPoint.token(accessCtx);
            StepVerifier.create(flux).assertNext(result -> {
                val obj     = (ObjectValue) result;
                val payload = (ObjectValue) obj.get("payload");
                val expVal  = payload.get("exp");
                assertThat(expVal).isNotNull();
                assertThat(expVal.toString()).contains("T");
            }).verifyComplete();
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
