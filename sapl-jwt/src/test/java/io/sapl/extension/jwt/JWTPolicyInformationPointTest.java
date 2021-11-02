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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.extension.jwt.TestMockServerDispatcher.DispatchMode;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

public class JWTPolicyInformationPointTest {

	private static KeyPair keyPair;

	private static WebClient.Builder builder;

	private static MockWebServer server;

	private static TestMockServerDispatcher dispatcher;

	private JWTPolicyInformationPoint jwtPolicyInformationPoint;

	@BeforeAll
	public static void preSetup() throws IOException {
		keyPair = KeyTestUtility.keyPair();
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

		jwtPolicyInformationPoint = new JWTPolicyInformationPoint(builder);
		Logger.getLogger(MockWebServer.class.getName()).setLevel(Level.OFF);
	}

	@Test
	public void validity_withNull_shouldBeMalformed() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var flux = jwtPolicyInformationPoint.validity(null, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withWrongType_shouldBeMalformed() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var flux = jwtPolicyInformationPoint.validity(Val.of(50_000L), null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutMalformedToken_shouldBeMalformed() {
		var source = Val.of("MALFORMED TOKEN");
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_ofNull_shouldBeMalformed() {
		var flux = jwtPolicyInformationPoint.validity(null, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_ofNonTextToken_shouldBeMalformed() {
		var source = Val.of(123);
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutKeyID_shouldBeIncomplete() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var source = JWTTestUtility.jwtWithoutKid(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withEmptyKeyID_shouldBeIncomplete() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var source = JWTTestUtility.jwtWithEmptyKid(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutIssuer_shouldBeIncomplete() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var source = JWTTestUtility.jwtWithoutIssuer(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutSubject_shouldBeIncomplete() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var source = JWTTestUtility.jwtWithoutSubject(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfAfterExp_shouldBeNeverValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtWithNbfAfterExp(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.NEVERVALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withExpBeforeNow_shouldBeExpired() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtExpired(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfBeforeNowAndWithoutExp_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtWithNbfBeforeNow(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExpAfterNow_shouldBeValidThenExpired() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtWithExpAfterNow(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndWithoutExp_shouldBeImmatureThenValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtWithNbfAfterNow(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndExpAfterNbf_shouldBeImmatureThenValidThenExpired() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwt(keyPair);
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, variables))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity - JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withEmptyEnvironment_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = new HashMap<String, JsonNode>();
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withWrongAlgorithm_shouldBeIncompatible() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = new HashMap<String, JsonNode>();
		var source = JWTTestUtility.jwtWithWrongAlgorithm(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
				.verifyComplete();
	}
	
	@Test
	public void valid_withWrongAlgorithm_shouldBeFalse() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = new HashMap<String, JsonNode>();
		var source = JWTTestUtility.jwtWithWrongAlgorithm(keyPair);
		var flux = jwtPolicyInformationPoint.valid(source, variables);
		StepVerifier.create(flux).expectNext(Val.FALSE)
				.verifyComplete();
	}

	@Test
	@Disabled
	public void validity_withoutNbfAndExp_withUriEnvironmentMissingUri_usingBase64Basic_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(null, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Basic_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.Basic);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Url_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Wrong_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.Invalid);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBogusKey_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.Bogus);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodPostEnvironment_usingBase64Url_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, "POST");
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBeValid() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, "NONETEXT");
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriBogusEnvironment_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.Unknown);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingWrongKey_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.Wrong);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtEternal(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withTamperedPayload_withUriEnvironment_shouldBeUntrusted() {
		dispatcher.setDispatchMode(DispatchMode.True);
		var variables = JsonTestUtility.publicKeyUriVariables(server, null);
		var source = JWTTestUtility.jwtWithTamperedPayload(keyPair);
		var flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

}
