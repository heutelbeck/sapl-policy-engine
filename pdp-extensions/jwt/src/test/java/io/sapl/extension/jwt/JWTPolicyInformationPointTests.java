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
package io.sapl.extension.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.validation.ValidatorFactory;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JWTPolicyInformationPointTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void contextIsAbleToLoadJWTPolicyInformationPoint() {
        final var mapper                = new ObjectMapper();
        final var validatorFactory      = new ValidatorFactory(mapper);
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        final var docsProvider          = new InMemoryPolicyInformationPointDocumentationProvider();
        final var pipLoader             = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                docsProvider, validatorFactory);
        assertDoesNotThrow(() -> pipLoader.loadPolicyInformationPoint(jwtPolicyInformationPoint));
    }

    /*
     * TEST INVALID KEY
     */

    @Test
    void validity_withInvalidKey_shouldBeUntrusted() throws JOSEException {
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        provider.cache(kid, KeyTestUtility.generateInvalidRSAPublicKey());
        final var flux = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST VALUE TYPES
     */

    @Test
    void validity_withWrongValueType_shouldBeMalformed() {
        final var flux = jwtPolicyInformationPoint.validity(Val.of(50_000L), null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withMalformedToken_shouldBeMalformed() {
        final var source = Val.of("MALFORMED TOKEN");
        final var flux   = jwtPolicyInformationPoint.validity(source, null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_ofNull_shouldBeMalformed() {
        final var flux = jwtPolicyInformationPoint.validity(null, null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
                .verifyComplete();
    }

    /*
     * TEST WHITELIST
     */

    @Test
    void validity_withWhitelist_emptyEntry_shouldBeUntrusted() throws JOSEException {

        final var variables = JsonTestUtility.publicKeyWhitelistVariables(kid, null, kid2, keyPair2);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withWhitelist_bogusEntry_shouldBeUntrusted() throws JOSEException {

        final var variables = JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid2).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair2);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withWhitelist_multiTest_shouldBeTrustedThenTrustedThenUntrusted()
            throws NoSuchAlgorithmException, IOException, JOSEException {

        final var keyPairs   = new KeyPair[] { keyPair, keyPair2, Base64DataUtil.generateRSAKeyPair() };
        final var variables  = JsonTestUtility.publicKeyWhitelistVariables(kid, keyPair, kid2, keyPair2);
        final var claims     = new JWTClaimsSet.Builder().build();
        final var validities = new ArrayList<Mono<Val>>();
        for (int trial = 0; trial < 3; trial++) {
            final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KeyTestUtility.kid(keyPairs[trial]))
                    .build();
            final var source = JWTTestUtility.buildAndSignJwt(header, claims, keyPairs[trial]);
            validities.add(jwtPolicyInformationPoint.validity(source, variables).last());
        }
        final var flux = Flux.concat(validities);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString())).verifyComplete();
    }

    /*
     * TEST ENVIRONMENT
     */

    @Test
    void validity_withEmptyEnvironment_shouldBeUntrusted() throws JOSEException {
        final var variables = Map.<String, Val>of();
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withUriEnvironmentMissingServer_shouldBeUntrusted() throws JOSEException {
        final var variables = Map.of("jwt", Val.ofEmptyObject());
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withUriEnvironment_usingWrongKey_shouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.WRONG);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withUriEnvironmentAndInvalidCachingTTL_usingBase64Url_shouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var jwtNode   = MAPPER.createObjectNode().set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY,
                JsonTestUtility.serverNode(server, null, "invalid TTL format"));
        final var variables = Map.of("jwt", Val.of(jwtNode));
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST HEADER CLAIMS
     */

    @Test
    void validity_withoutKeyID_shouldBeIncomplete() throws JOSEException {
        final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        final var claims = new JWTClaimsSet.Builder().build();
        final var source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux   = jwtPolicyInformationPoint.validity(source, null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withEmptyKeyID_shouldBeIncomplete() throws JOSEException {
        final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("").build();
        final var claims = new JWTClaimsSet.Builder().build();
        final var source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux   = jwtPolicyInformationPoint.validity(source, null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withCriticalHeader_shouldBeIncompatible() throws JOSEException {
        final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).criticalParams(Set.of("critparam"))
                .build();
        final var claims = new JWTClaimsSet.Builder().build();
        final var source = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux   = jwtPolicyInformationPoint.validity(source, null);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
                .verifyComplete();
    }

    /*
     * TEST SIGNATURE
     */

    @Test
    void validity_withWrongAlgorithm_shouldBeIncompatible() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = Map.<String, Val>of();
        final var header    = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
                .verifyComplete();
    }

    @Test
    void valid_withWrongAlgorithm_shouldBeFalse() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = Map.<String, Val>of();
        final var header    = new JWSHeader.Builder(JWSAlgorithm.PS512).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.valid(source, variables);
        StepVerifier.create(flux).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void validity_withTamperedPayload_withUriEnvironment_shouldBeUntrusted() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables      = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header         = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims         = new JWTClaimsSet.Builder().build();
        final var tamperedClaims = new JWTClaimsSet.Builder().jwtID("").build();
        final var originalJWT    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var source         = JWTTestUtility.replacePayload(originalJWT, tamperedClaims);
        final var flux           = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
                .verifyComplete();
    }

    /*
     * TEST TIME CLAIMS
     */

    @Test
    void validity_withNbfAfterExp_shouldBeNeverValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow())
                .notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.NEVER_VALID.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withExpBeforeNow_shouldBeExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withExpAfterNow_shouldBeValidThenExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().expirationTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
    }

    @Test
    void validity_withNbfAfterNow_shouldBeImmatureThenValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitAfterNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString())).verifyComplete();
    }

    @Test
    void validity_withNbfBeforeNow_shouldBeValid() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitBeforeNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        final var flux      = jwtPolicyInformationPoint.validity(source, variables);
        StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .verifyComplete();
    }

    @Test
    void validity_withNbfAfterNowAndExpAfterNbf_shouldBeImmatureThenValidThenExpired() throws JOSEException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var variables = JsonTestUtility.publicKeyUriVariables(server, null);
        final var header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        final var claims    = new JWTClaimsSet.Builder().notBeforeTime(JWTTestUtility.timeOneUnitAfterNow())
                .expirationTime(JWTTestUtility.timeThreeUnitsAfterNow()).build();
        final var source    = JWTTestUtility.buildAndSignJwt(header, claims, keyPair);
        StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
                .thenAwait(JWTTestUtility.twoUnitDuration())
                .expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
    }

}
