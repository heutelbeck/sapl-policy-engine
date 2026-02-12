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
import io.sapl.api.model.TextValue;
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
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.sapl.attributes.libraries.JWTPolicyInformationPointTests.CtxUtil.ctx;
import static io.sapl.attributes.libraries.JWTPolicyInformationPointTests.CtxUtil.emptyCtx;
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

    /*
     * TEST INVALID KEY
     */

    @Test
    void validityWithInvalidKeyShouldBeUntrusted() throws JOSEException {
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        provider.cache(kid, KeyTestUtility.generateInvalidRSAPublicKey());
        val flux = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST VALUE TYPES
     */

    @Test
    void validityWithWrongValueTypeShouldBeMalformed() {
        val flux = jwtPolicyInformationPoint.validity((TextValue) Value.of("50000"), emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithMalformedTokenShouldBeMalformed() {
        val source = Value.of("MALFORMED TOKEN");
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    @Test
    void validityOfNullShouldBeMalformed() {
        val flux = jwtPolicyInformationPoint.validity(null, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    /*
     * TEST WHITELIST
     */

    @Test
    void validityWithWhitelistEmptyEntryShouldBeUntrusted() throws JOSEException {
        val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, null, kid2, keyPair2));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithWhitelistBogusEntryShouldBeUntrusted() throws JOSEException {
        val accessCtx = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid2).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair2);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithWhitelistMultiTestShouldBeTrustedThenTrustedThenUntrusted()
            throws NoSuchAlgorithmException, IOException, JOSEException {
        val keyPairs   = new KeyPair[] { keyPair, keyPair2, Base64DataUtil.generateRSAKeyPair() };
        val accessCtx  = ctx(JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2));
        val claims     = new JWTClaimsSet.Builder().build();
        val validities = new ArrayList<Mono<Value>>();
        for (int trial = 0; trial < 3; trial++) {
            val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KeyTestUtility.kid(keyPairs[trial])).build();
            val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPairs[trial]);
            validities.add(jwtPolicyInformationPoint.validity(source, accessCtx).last());
        }
        val flux = Flux.concat(validities);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString())).verifyComplete();
    }

    /*
     * TEST ENVIRONMENT
     */

    @Test
    void validityWithEmptyEnvironmentShouldBeUntrusted() throws JOSEException {
        val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithUriEnvironmentMissingServerShouldBeUntrusted() throws JOSEException {
        val accessCtx = ctx(Map.of("jwt", Value.EMPTY_OBJECT));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithUriEnvironmentUsingWrongKeyShouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.WRONG);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithUriEnvironmentAndInvalidCachingTtlUsingBase64UrlShouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val jwtNode   = MAPPER.createObjectNode().set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY,
                JsonTestUtility.serverNode(server, null, "invalid TTL format"));
        val accessCtx = ctx(Map.of("jwt", ValueJsonMarshaller.fromJsonNode(jwtNode)));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST HEADER CLAIMS
     */

    @Test
    void validityWithoutKeyIdShouldBeIncomplete() throws JOSEException {
        val header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithEmptyKeyIdShouldBeIncomplete() throws JOSEException {
        val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("").build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithCriticalHeaderShouldBeIncompatible() throws JOSEException {
        val header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).criticalParams(Set.of("critparam")).build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
                .verifyComplete();
    }

    /*
     * TEST SIGNATURE
     */

    @Test
    void validityWithWrongAlgorithmShouldBeIncompatible() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val header = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.validity(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
                .verifyComplete();
    }

    @Test
    void validWithWrongAlgorithmShouldBeFalse() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val header = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
        val claims = new JWTClaimsSet.Builder().build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux   = jwtPolicyInformationPoint.valid(source, emptyCtx());
        StepVerifier.create(flux).expectNext(Value.FALSE).verifyComplete();
    }

    @Test
    void validityWithTamperedPayloadWithUriEnvironmentShouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx      = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header         = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims         = new JWTClaimsSet.Builder().build();
        val tamperedClaims = new JWTClaimsSet.Builder().jwtID("").build();
        val originalJWT    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val source         = JWTTestUtility.replacePayload(originalJWT, tamperedClaims);
        val flux           = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST TIME CLAIMS
     */

    @Test
    void validityWithNbfAfterExpShouldBeNeverValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow())
                .notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.NEVER_VALID.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithExpBeforeNowShouldBeExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithExpAfterNowShouldBeValidThenExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        provider.cache(kid, (RSAPublicKey) keyPair.getPublic());
        StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, accessCtx))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
    }

    @Test
    void validityWithNbfAfterNowShouldBeImmatureThenValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        provider.cache(kid, (RSAPublicKey) keyPair.getPublic());
        StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, accessCtx))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString())).verifyComplete();
    }

    @Test
    void validityWithNbfBeforeNowShouldBeValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        val claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
        val source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        val flux      = jwtPolicyInformationPoint.validity(source, accessCtx);
        StepVerifier.create(flux).expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .verifyComplete();
    }

    @Test
    void validityWithNbfAfterNowAndExpAfterNbfShouldBeImmatureThenValidThenExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        val accessCtx = ctx(JsonTestUtility.publicKeyUriVariables(server, null));
        val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();

        val nbf = Date.from(Instant.now().plusSeconds(2));
        val exp = Date.from(Instant.now().plusSeconds(4));

        val claims = new JWTClaimsSet.Builder().notBeforeTime(nbf).expirationTime(exp).build();
        val source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);

        StepVerifier.create(jwtPolicyInformationPoint.validity(source, accessCtx))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .expectNext(Value.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).thenCancel()
                .verify(Duration.ofSeconds(6));
    }

    static class CtxUtil {
        static AttributeAccessContext emptyCtx() {
            return new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        }

        static AttributeAccessContext ctx(Map<String, Value> pdpSecrets) {
            if (pdpSecrets == null || pdpSecrets.isEmpty()) {
                return emptyCtx();
            }
            val builder = ObjectValue.builder();
            pdpSecrets.forEach(builder::put);
            return new AttributeAccessContext(Value.EMPTY_OBJECT, builder.build(), Value.EMPTY_OBJECT);
        }
    }

}
