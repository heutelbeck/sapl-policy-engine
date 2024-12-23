/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import io.sapl.extension.jwt.JWTKeyProvider.CachingException;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JWTKeyProviderTests {

    private static String kid;

    private static String otherKid;

    private static MockWebServer server;

    private static TestMockServerDispatcher dispatcher;

    private static WebClient.Builder builder;

    private static KeyPair keyPair;

    private static KeyPair otherKeyPair;

    private JWTKeyProvider provider;

    @BeforeAll
    public static void preSetup() throws IOException, NoSuchAlgorithmException {
        Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.OFF);
        keyPair      = Base64DataUtil.generateRSAKeyPair();
        kid          = KeyTestUtility.kid(keyPair);
        otherKeyPair = Base64DataUtil.generateRSAKeyPair();
        otherKid     = KeyTestUtility.kid(otherKeyPair);
        server       = KeyTestUtility.testServer(Set.of(keyPair, otherKeyPair));
        dispatcher   = (TestMockServerDispatcher) server.getDispatcher();
        server.start();
        builder = WebClient.builder();
    }

    @AfterAll
    public static void teardown() throws IOException {
        server.close();
    }

    @BeforeEach
    void setup() {
        provider = new JWTKeyProvider(builder);
    }

    /*
     * TEST CACHING
     */

    @Test
    void isCached_notCachedThenCachedThenNotCached_shouldBeFalseThenTrueThenFalse() {
        final var pubKey = (RSAPublicKey) keyPair.getPublic();
        provider.setTtlMillis(JWTTestUtility.SYNCHRONOUS_TIME_UNIT);
        assertFalse(provider.isCached(kid));
        provider.cache(kid, pubKey);
        assertTrue(provider.isCached(kid));
        Mono.delay(JWTTestUtility.twoSynchronousUnitDuration()).block();
        assertFalse(provider.isCached(kid));
    }

    @Test
    void isCached_cacheTwice_shouldBeFalseThenTrueThenTrue() {
        final var pubKey = (RSAPublicKey) keyPair.getPublic();
        assertFalse(provider.isCached(kid));
        provider.cache(kid, pubKey);
        assertTrue(provider.isCached(kid));
        provider.cache(kid, pubKey);
        assertTrue(provider.isCached(kid));
    }

    @Test
    void provide_cacheThenRetrieve_shouldBeFalseThenTrueThenPublicKey() throws CachingException {
        final var pubKey     = (RSAPublicKey) keyPair.getPublic();
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        assertFalse(provider.isCached(kid));
        provider.cache(kid, pubKey);
        assertTrue(provider.isCached(kid));
        final var mono = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void isCachedAndProvide_multipleKeys_shouldBeTwiceFalseThenPublicKeyThenTrueThenFalseThenPublicKeyTheTwiceTrueThenFalse()
            throws CachingException {

        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        assertFalse(provider.isCached(kid));
        assertFalse(provider.isCached(otherKid));
        final var firstRetrievedKey = provider.provide(kid, serverNode).block(JWTTestUtility.twoUnitDuration());
        assertTrue(KeyTestUtility.areKeysEqual(firstRetrievedKey, keyPair));
        provider.cache(kid, firstRetrievedKey);
        assertTrue(provider.isCached(kid));
        assertFalse(provider.isCached(otherKid));
        final var secondRetrievedKey = provider.provide(otherKid, serverNode).block(JWTTestUtility.twoUnitDuration());
        assertTrue(KeyTestUtility.areKeysEqual(secondRetrievedKey, otherKeyPair));
        provider.cache(otherKid, secondRetrievedKey);
        assertTrue(provider.isCached(kid));
        assertTrue(provider.isCached(otherKid));
        assertNotNull(firstRetrievedKey);
        assertNotNull(secondRetrievedKey);
        assertFalse(KeyTestUtility.areKeysEqual(firstRetrievedKey, secondRetrievedKey));
    }

    /*
     * TEST ENVIRONMENT
     */

    @Test
    void provide_withUriEnvironmentMissingUri_shouldBeEmpty() throws CachingException {
        final var serverNode = JsonTestUtility.serverNode(null, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).verifyComplete();
    }

    @Test
    void provide_withUriEnvironmentAndInvalidCachingTTL_usingBase64Url_shouldThrowCachingException() {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, null, "invalid TTL format");
        assertThrows(CachingException.class, () -> provider.provide(kid, serverNode));
    }

    @Test
    void provide_withUriEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriAndMethodPostEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, "POST", null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, "NONETEXT", null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriAndCustomTTLEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, null, JWTTestUtility.TIME_UNIT);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriAndNegativeTTLEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.TRUE);
        final var serverNode = JsonTestUtility.serverNode(server, null, -JWTTestUtility.TIME_UNIT);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriEnvironment_usingBase64Basic_shouldBePublicKey() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.BASIC);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
    }

    @Test
    void provide_withUriEnvironment_usingBase64Wrong_shouldBeEmpty() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.INVALID);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).verifyComplete();
    }

    @Test
    void provide_withUriEnvironment_usingBogusKey_shouldBeEmpty() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.BOGUS);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).verifyComplete();
    }

    @Test
    void provide_withUriBogusEnvironment_shouldBeEmpty() throws CachingException {
        dispatcher.setDispatchMode(DispatchMode.UNKNOWN);
        final var serverNode = JsonTestUtility.serverNode(server, null, null);
        final var mono       = provider.provide(kid, serverNode);
        StepVerifier.create(mono).verifyComplete();
    }

}
