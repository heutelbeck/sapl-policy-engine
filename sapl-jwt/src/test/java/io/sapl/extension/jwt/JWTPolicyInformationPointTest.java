/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class)
public class JWTPolicyInformationPointTest {

	private static KeyPair keyPair;

	private static WebClient.Builder builder;

	private MockWebServer server;

	private JWTPolicyInformationPoint jwtPolicyInformationPoint;

	@BeforeClass
	public static void preSetup() {
		keyPair = KeyTestUtility.keyPair();
		/*log.info("Testing with key pair:\nPrivate:\t{}\nPublic:\t{}",
				Base64URL.encode(keyPair.getPrivate().getEncoded()).toString(),
				Base64URL.encode(keyPair.getPublic().getEncoded()).toString());*/
		builder = WebClient.builder();
	}

	@Before
	public void setup() throws IOException {
		server = new MockWebServer();
		jwtPolicyInformationPoint = new JWTPolicyInformationPoint(builder);
	}

	@After
	public void teardown() throws IOException {
		server.close();
	}

	@Test
	public void validity_withNull_shouldBeMalformed() {
		Flux<Val> flux = jwtPolicyInformationPoint.validity(null, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	/*
	 * @Test public void validity_withoutType_shouldBeMalformed() { final Val source
	 * = JWTTestUtility.jwtWithoutType(keyPair); Flux<Val> flux =
	 * jwtPolicyInformationPoint.validity(source, null);
	 * StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.
	 * ValidityState.MALFORMED.toString())) .verifyComplete(); }
	 */

	/*
	 * @Test public void validity_withWrongType_shouldBeMalformed() { final Val
	 * source = JWTTestUtility.jwtWithWrongType(keyPair); Flux<Val> flux =
	 * jwtPolicyInformationPoint.validity(source, null);
	 * StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.
	 * ValidityState.MALFORMED.toString())) .verifyComplete(); }
	 */

	@Test
	public void validity_withoutKeyID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutKid(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withEmptyKeyID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithEmptyKid(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutIssuer_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutIssuer(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutSubject_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutSubject(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfAfterExp_shouldBeNeverValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtWithNbfAfterExp(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.NEVERVALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withExpBeforeNow_shouldBeExpired() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtExpired(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfBeforeNowAndWithoutExp_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtWithNbfBeforeNow(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExpAfterNow_shouldBeValidThenExpired() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtWithExpAfterNow(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndWithoutExp_shouldBeImmatureThenValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtWithNbfAfterNow(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndExpAfterNbf_shouldBeImmatureThenValidThenExpired() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwt(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity - JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withEmptyEnvironment_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = new HashMap<String, JsonNode>();
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironmentMissingUri_usingBase64Basic_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(null, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Basic_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Basic(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Wrong_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Invalid(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBogusKey_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Bogus()));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodPostEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, "POST");
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, "NONETEXT");
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriBogusEnvironment_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		// bogus environment is simulated by server responding with 404
		server.enqueue(new MockResponse().setResponseCode(404));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingWrongKey_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		// send public key of different key pair
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200")
				.setBody(KeyTestUtility.base64Url(KeyTestUtility.keyPair())));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withTamperedPayload_withUriEnvironment_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtWithTamperedPayload(keyPair);
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

}
