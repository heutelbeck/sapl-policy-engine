package io.sapl.extension.jwt;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import io.sapl.extension.jwt.TestMockServerDispatcher.DispatchMode;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class JWTKeyProviderTest {
	
	private static String kid;
	private static MockWebServer server;
	private static TestMockServerDispatcher dispatcher;
	private static WebClient.Builder builder;
	private static JWTKeyProvider provider;
	private static KeyPair keyPair;

	@BeforeAll
	public static void preSetup() throws IOException, NoSuchAlgorithmException {
		Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.OFF);
		keyPair = KeyTestUtility.generateRSAKeyPair();
		kid = KeyTestUtility.kid(keyPair);
		server = KeyTestUtility.testServer("/public-keys/", keyPair);
		dispatcher = (TestMockServerDispatcher) server.getDispatcher();
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
	@Disabled
	public void isCached_notCachedThenCached_shouldBeFalseThenTrue() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var flux = Flux.concat(Mono.just(provider.isCached(kid)),
				Mono.just(Boolean.FALSE).map(x -> {
					provider.cache(kid, (RSAPublicKey)keyPair.getPublic());
					return x;
				})
				.then(Mono.just(provider.isCached(kid))));
		StepVerifier.create(flux).expectNext(Boolean.FALSE).expectNext(Boolean.TRUE).verifyComplete();
	}

	@Test
	@Disabled
	public void provide_notCachedThenCachedThenNotCached_shouldBeFalseThenPublicKeyThenTrueThenTrueThenPublicKeyThenFalse() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, JWTTestUtility.twoUnitDuration().toMillis());
		Supplier<Flux<Object>> fluxSupplier = () -> Flux.concat(Mono.just(provider.isCached(kid)),
				Mono.just((RSAPublicKey) keyPair.getPublic()).map(JWTTestUtility.cacheAndReturn(provider, kid)),
				Mono.just(provider.isCached(kid)),
				Mono.just(provider.isCached(kid)).delayElement(JWTTestUtility.oneUnitDuration()),
				provider.provide(kid, serverNode),
				Mono.just(provider.isCached(kid)).delayElement(JWTTestUtility.twoUnitDuration()));
		StepVerifier.withVirtualTime(fluxSupplier).expectNext(Boolean.FALSE)
				.expectNextMatches(KeyTestUtility.keyValidator(keyPair))
				.expectNext(Boolean.TRUE)
				.thenAwait(JWTTestUtility.twoUnitDuration())
				.expectNext(Boolean.TRUE)
				.expectNextMatches(KeyTestUtility.keyValidator(keyPair))
				.thenAwait(JWTTestUtility.twoUnitDuration())
				.expectNext(Boolean.FALSE).verifyComplete();
	}

/*
 * TEST ENVIRONMENT
 */

	@Test
	public void provide_withUriEnvironmentMissingUri_shouldBeEmpty() {
		var serverNode = JsonTestUtility.serverNode(null, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}
	
	@Test
	public void provide_withUriEnvironment_usingBase64Url_shouldBePublicKey() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}
	
	@Test
	public void provide_withUriAndMethodPostEnvironment_usingBase64Url_shouldBePublicKey() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, "POST", null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBePublicKey() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, "NONETEXT", null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}
	
	@Test
	public void provide_withUriAndCustomTTLEnvironment_usingBase64Url_shouldBePublicKey() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var serverNode = JsonTestUtility.serverNode(server, null, JWTTestUtility.timeUnit);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBase64Basic_shouldBePublicKey() {
		dispatcher.setDispatchMode(DispatchMode.Basic);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).expectNextMatches(KeyTestUtility.keyValidator(keyPair)).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBase64Wrong_shouldBeEmpty() {
		dispatcher.setDispatchMode(DispatchMode.Invalid);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

	@Test
	public void provide_withUriEnvironment_usingBogusKey_shouldBeEmpty() {
		dispatcher.setDispatchMode(DispatchMode.Bogus);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}

	@Test
	public void provide_withUriBogusEnvironment_shouldBeEmpty() {
		dispatcher.setDispatchMode(DispatchMode.Unknown);
		var serverNode = JsonTestUtility.serverNode(server, null, null);
		var mono = provider.provide(kid, serverNode);
		StepVerifier.create(mono).verifyComplete();
	}
	
}
