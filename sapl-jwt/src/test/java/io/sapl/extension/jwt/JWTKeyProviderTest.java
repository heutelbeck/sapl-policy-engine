/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.extension.jwt.TestMockServerDispatcher.DispatchMode;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class JWTKeyProviderTest {

	private static String kid;

	private static String otherKid;

	private static MockWebServer server;

	private static TestMockServerDispatcher dispatcher;

	private static WebClient.Builder builder;

	private static JWTKeyProvider provider;

	private static KeyPair keyPair;

	private static KeyPair otherKeyPair;

	@BeforeAll
	public static void preSetup() throws IOException, NoSuchAlgorithmException {
		Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.OFF);
		keyPair      = KeyTestUtility.generateRSAKeyPair();
		kid          = KeyTestUtility.kid(keyPair);
		otherKeyPair = KeyTestUtility.generateRSAKeyPair();
		otherKid     = KeyTestUtility.kid(otherKeyPair);
		server       = KeyTestUtility.testServer("/public-keys/", Set.of(keyPair, otherKeyPair));
		dispatcher   = (TestMockServerDispatcher) server.getDispatcher();
		server.start();
		builder = WebClient.builder();
	}

	@AfterAll
	public static void teardown() throws IOException {
		server.close();
	}

	@BeforeEach
	public void setup() {
		provider = new JWTKeyProvider(builder);
	}

	/*
	 * TEST CACHING
	 */

	@Test
	public void isCached_notCachedThenCachedThenNotCached_shouldBeFalseThenTrueThenFalse() {
		var pubKey = (RSAPublicKey) keyPair.getPublic();
		provider.setTTLmillis(JWTTestUtility.synchronousTimeUnit);
		assertFalse(provider.isCached(kid));
		provider.cache(kid, pubKey);
		assertTrue(provider.isCached(kid));
		Mono.delay(JWTTestUtility.twoSynchronousUnitDuration()).block();
		assertFalse(provider.isCached(kid));
	}

	@Test
	public void isCached_cacheTwice_shouldBeFalseThenTrueThenTrue() {
		var pubKey = (RSAPublicKey) keyPair.getPublic();
		assertFalse(provider.isCached(kid));
		provider.cache(kid, pubKey);
		assertTrue(provider.isCached(kid));
		provider.cache(kid, pubKey);
		assertTrue(provider.isCached(kid));
	}

	@Test
	public void provide_cacheThenRetrieve_shouldBeFalseThenTrueThenPublicKey() throws CachingException {
		var pubKey     = (RSAPublicKey) keyPair.getPublic();
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		assertFalse(provider.isCached(kid));
		provider.cache(kid, pubKey);
		assertTrue(provider.isCached(kid));
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void isCachedAndProvide_multipleKeys_shouldBeTwiceFalseThenPublicKeyThenTrueThenFalseThenPublicKeyTheTwiceTrueThenFalse()
			throws NoSuchAlgorithmException, IOException, CachingException {

		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		assertFalse(provider.isCached(kid));
		assertFalse(provider.isCached(otherKid));
		var firstRetrievedKey = provider.provide(kid, serverNode).block(JWTTestUtility.twoUnitDuration());
		assertTrue(KeyTestUtility.areKeysEqual(firstRetrievedKey, keyPair));
		provider.cache(kid, firstRetrievedKey);
		assertTrue(provider.isCached(kid));
		assertFalse(provider.isCached(otherKid));
		var secondRetrievedKey = provider.provide(otherKid, serverNode).block(JWTTestUtility.twoUnitDuration());
		assertTrue(KeyTestUtility.areKeysEqual(secondRetrievedKey, otherKeyPair));
		provider.cache(otherKid, secondRetrievedKey);
		assertTrue(provider.isCached(kid));
		assertTrue(provider.isCached(otherKid));
		assertFalse(KeyTestUtility.areKeysEqual(firstRetrievedKey, secondRetrievedKey));
	}

	/*
	 * TEST ENVIRONMENT
	 */

	@Test
	public void provide_withUriEnvironmentMissingUri_shouldBeEmpty() throws CachingException {
		var serverNode = JsonTestUtility.serverNode(null, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironmentAndInvalidCachingTTL_usingBase64Url_shouldThrowCachingException() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, "invalid TTL format");
		assertThrows(CachingException.class, () -> provider.provide(kid, serverNode));
	}

	@Test
	public void provide_withUriAndMethodPostEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, "POST", null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, "NONETEXT", null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriAndCustomTTLEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, JWTTestUtility.timeUnit);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriAndNegativeTTLEnvironment_usingBase64Url_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, -JWTTestUtility.timeUnit);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBase64Basic_shouldBePublicKey() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.Basic);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBase64Wrong_shouldBeEmpty() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.Invalid);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBogusKey_shouldBeEmpty() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.Bogus);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

	@Test
	public void provide_withUriBogusEnvironment_shouldBeEmpty() throws CachingException {
		dispatcher.setDispatchMode(DispatchMode.Unknown);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono       = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

}
