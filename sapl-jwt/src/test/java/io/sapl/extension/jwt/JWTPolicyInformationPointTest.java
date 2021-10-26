package io.sapl.extension.jwt;

import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
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

	@Mock
	private JWTLibraryService jwtService;

	private MockWebServer server;

	private JWTPolicyInformationPoint jwtPolicyInformationPoint;

	@BeforeClass
	public static void preSetup() {
		keyPair = KeyTestUtility.keyPair();
		builder = WebClient.builder();
	}

	@Before
	public void setup() {
		server = new MockWebServer();
		jwtPolicyInformationPoint = new JWTPolicyInformationPoint(jwtService, builder);
	}

	@After
	public void teardown() throws IOException {
		server.close();
	}

	@Test
	public void validity_withNull_shouldBeMalformed() {
		when(jwtService.resolveToken(null)).thenReturn(Val.UNDEFINED);
		Flux<Val> flux = jwtPolicyInformationPoint.validity(null, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutType_shouldBeMalformed() {
		final Val source = JWTTestUtility.jwtWithoutType(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withWrongType_shouldBeMalformed() {
		final Val source = JWTTestUtility.jwtWithWrongType(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.MALFORMED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutKeyID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutKid(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withEmptyKeyID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithEmptyKid(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutId(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withEmptyID_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithEmptyId(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutIssuer_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutIssuer(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutSubject_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutSubject(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutAuthorities_shouldBeIncomplete() {
		final Val source = JWTTestUtility.jwtWithoutAuthorities(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPLETE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withWrongAlgorithm_shouldBeIncompatible() {
		final Val source = JWTTestUtility.jwtWithWrongAlgorithm(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withWrongAuthorities_shouldBeIncompatible() {
		final Val source = JWTTestUtility.jwtWithWrongAuthorities(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.INCOMPATIBLE.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfAfterExp_shouldBeNeverValid() {
		final Val source = JWTTestUtility.jwtWithNbfAfterExp(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.NEVERVALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withExpBeforeNow_shouldBeExpired() {
		final Val source = JWTTestUtility.jwtExpired(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_shouldBeValid() {
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withNbfBeforeNowAndWithoutExp_shouldBeValid() {
		final Val source = JWTTestUtility.jwtWithNbfBeforeNow(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, null);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExpAfterNow_shouldBeValidThenExpired() {
		final Val source = JWTTestUtility.jwtWithExpAfterNow(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, null))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndWithoutExp_shouldBeImmatureThenValid() {
		final Val source = JWTTestUtility.jwtWithNbfAfterNow(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, null))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString())).verifyComplete();
	}

	@Test
	public void validity_withNbfAfterNowAndExpAfterNbf_shouldBeImmatureThenValidThenExpired() {
		final Val source = JWTTestUtility.jwt(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		StepVerifier.withVirtualTime(() -> jwtPolicyInformationPoint.validity(source, null))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.IMMATURE.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.thenAwait(Duration.ofMillis(JWTTestUtility.tokenValidity - JWTTestUtility.tokenMaturity))
				.expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.EXPIRED.toString())).verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withEmptyEnvironment_shouldBeValid() {
		final Map<String, JsonNode> variables = new HashMap<String, JsonNode>();
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironmentMissingUri_usingBase64Basic_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(null, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Basic_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		when(jwtService.signedJwt(source.getText())).thenReturn(JWTTestUtility.signedJwt(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Basic(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		when(jwtService.signedJwt(source.getText())).thenReturn(JWTTestUtility.signedJwt(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBase64Wrong_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Invalid(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriEnvironment_usingBogusKey_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Bogus()));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodPostEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, "POST");
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		when(jwtService.signedJwt(source.getText())).thenReturn(JWTTestUtility.signedJwt(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriAndMethodNonTextEnvironment_usingBase64Url_shouldBeValid() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, "NONETEXT");
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		when(jwtService.signedJwt(source.getText())).thenReturn(JWTTestUtility.signedJwt(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.VALID.toString()))
				.verifyComplete();
	}

	@Test
	public void validity_withoutNbfAndExp_withUriBogusEnvironment_shouldBeUntrusted() {
		final Map<String, JsonNode> variables = JsonTestUtility.publicKeyUriVariables(server, null);
		final Val source = JWTTestUtility.jwtEternal(keyPair);
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
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
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
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
		when(jwtService.resolveToken(source)).thenReturn(source);
		when(jwtService.header(source.getText())).thenReturn(JWTTestUtility.header(source));
		when(jwtService.claims(source.getText())).thenReturn(JWTTestUtility.claims(source));
		when(jwtService.signedJwt(source.getText())).thenReturn(JWTTestUtility.signedJwt(source));
		server.enqueue(new MockResponse().setStatus("HTTP/1.1 200").setBody(KeyTestUtility.base64Url(keyPair)));
		Flux<Val> flux = jwtPolicyInformationPoint.validity(source, variables);
		StepVerifier.create(flux).expectNext(Val.of(JWTPolicyInformationPoint.ValidityState.UNTRUSTED.toString()))
				.verifyComplete();
	}

}
