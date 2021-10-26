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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.text.ParseException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;

public class JWTLibraryServiceTest {

	private static KeyPair keyPair;

	private JWTLibraryService jwtLibraryService;

	@BeforeClass
	public static void preSetup() {
		keyPair = KeyTestUtility.keyPair();
	}

	@Before
	public void setup() {
		jwtLibraryService = new JWTLibraryService(JsonTestUtility.getMapper());
	}

	@Test
	public void signedJwt_withNull_shouldBeNull() {
		assertNull(jwtLibraryService.signedJwt(null));
	}

	@Test
	public void signedJwt_withUnsignedJwt_shouldBeNull() {
		assertNull(jwtLibraryService.signedJwt(JWTTestUtility.unsignedJwt().getText()));
	}

	@Test
	public void signedJwt_withProperJwt_shouldBeSignedJwt() {
		assertThat(jwtLibraryService.signedJwt(JWTTestUtility.jwt(keyPair).getText()), instanceOf(SignedJWT.class));
	}

	@Test
	public void header_withNull_shouldBeNull() {
		assertNull(jwtLibraryService.header(null));
	}

	@Test
	public void header_withProperJwt_shouldBeJWSHeader() {
		assertThat(jwtLibraryService.header(JWTTestUtility.jwt(keyPair).getText()), instanceOf(JWSHeader.class));
	}

	@Test
	public void claims_withNull_shouldBeNull() {
		assertNull(jwtLibraryService.claims(null));
	}

	@Test
	public void claims_withProperJwt_shouldBeJWTClaimsSet() {
		assertThat(jwtLibraryService.claims(JWTTestUtility.jwt(keyPair).getText()), instanceOf(JWTClaimsSet.class));
	}

	@Test
	public void isJwt_withNull_shouldBeFalse() {
		assertFalse(jwtLibraryService.isJwt(null));
	}

	@Test
	public void isJwt_withUnsignedJwt_shouldBeFalse() {
		assertFalse(jwtLibraryService.isJwt(JWTTestUtility.unsignedJwt().getText()));
	}

	@Test
	public void isJwt_withProperJwt_shouldBeTrue() {
		assertTrue(jwtLibraryService.isJwt(JWTTestUtility.jwt(keyPair).getText()));
	}

	@Test
	public void resolveToken_withNull_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(null));
	}

	@Test
	public void resolveToken_withUnsignedJwt_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(JWTTestUtility.unsignedJwt()));
	}

	@Test
	public void resolveToken_withProperJwt_shouldBeExpectedJWT() {
		Val jwt = JWTTestUtility.jwt(keyPair);
		assertEquals(jwt, jwtLibraryService.resolveToken(jwt));
	}

	@Test
	public void resolveToken_withProperJwtInProperScheme_shouldBeExpectedJwt() {
		Val jwt = JWTTestUtility.jwt(keyPair);
		assertEquals(jwt, jwtLibraryService.resolveToken(JWTTestUtility.jwtInProperScheme(jwt.getText())));
	}

	@Test
	public void resolveToken_withProperJwtInImproperScheme_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(JWTTestUtility.properJwtInImproperScheme(keyPair)));
	}

	@Test
	public void resolveToken_withProperJwtInProperHeader_shouldBeExpectedJwt() {
		Val jwt = JWTTestUtility.jwt(keyPair);
		assertEquals(jwt, jwtLibraryService.resolveToken(JWTTestUtility.jwtInProperHeader(jwt.getText())));
	}

	@Test
	public void resolveToken_withProperJwtInLowerCaseHeader_shouldBeExpectedJwt() {
		Val jwt = JWTTestUtility.jwt(keyPair);
		assertEquals(jwt, jwtLibraryService.resolveToken(JWTTestUtility.jwtInLowerCaseHeader(jwt.getText())));
	}

	@Test
	public void resolveToken_withImproperJwtInProperHeader_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(JWTTestUtility.improperJwtInProperHeader()));
	}

	@Test
	public void resolveToken_withProperJwtInProperHeaderWithImproperScheme_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED,
				jwtLibraryService.resolveToken(JWTTestUtility.properJwtInProperHeaderWithImproperScheme(keyPair)));
	}

	@Test
	public void resolveToken_withProperJwtInProperHeaderWithEmptyScheme_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED,
				jwtLibraryService.resolveToken(JWTTestUtility.properJwtInProperHeaderWithEmptyScheme(keyPair)));
	}

	@Test
	public void resolveToken_withProperJwtInImproperHeader_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(JWTTestUtility.properJwtInImproperHeader(keyPair)));
	}

	@Test
	public void resolveToken_withImproperJwt_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.resolveToken(JWTTestUtility.bogusJwt()));
	}

	@Test
	public void resolveToken_withProperJwtInProperCredentials_shouldBeExpectedJwt() {
		Val jwt = JWTTestUtility.jwt(keyPair);
		assertEquals(jwt, jwtLibraryService.resolveToken(JWTTestUtility.jwtInProperCredentials(jwt.getText())));
	}

	@Test
	public void resolveToken_withImproperJwtInProperCredentials_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService
				.resolveToken(JWTTestUtility.jwtInProperCredentials(JWTTestUtility.bogusJwt().getText())));
	}

	@Test
	public void value_ofExistingKey_shouldBeExpectedValue() {
		assertEquals(Val.of(JWTTestUtility.subject),
				jwtLibraryService.value(JWTTestUtility.jwt(keyPair), Val.of("sub")));
	}

	@Test
	public void value_ofBogusKey_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.value(JWTTestUtility.jwt(keyPair), Val.of("bogusKey")));
	}

	@Test
	public void value_ofList_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED,
				jwtLibraryService.value(JWTTestUtility.jwt(keyPair), Val.of(JWTLibraryService.AUTHORITIES_KEY)));
	}

	@Test
	public void values_ofExistingKey_shouldBeExpectedList() {
		assertEquals(Val.of(JsonTestUtility.jsonNode(JWTTestUtility.authorities)),
				jwtLibraryService.values(JWTTestUtility.jwt(keyPair), Val.of(JWTLibraryService.AUTHORITIES_KEY)));
	}

	@Test
	public void values_ofBogusKey_shouldBeEmpty() {
		assertEquals(Val.ofEmptyArray(), jwtLibraryService.values(JWTTestUtility.jwt(keyPair), Val.of("bogusKey")));
	}

	@Test
	public void values_ofString_shouldBeEmpty() {
		assertEquals(Val.ofEmptyArray(), jwtLibraryService.values(JWTTestUtility.jwt(keyPair), Val.of("sub")));
	}

	@Test
	public void issuer_ofProperJwt_shouldBeExpectedIssuer() {
		assertEquals(Val.of(JWTTestUtility.trustedIssuer), jwtLibraryService.issuer(JWTTestUtility.jwt(keyPair)));
	}

	@Test
	public void issuer_ofJwtWithoutIssuer_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.issuer(JWTTestUtility.jwtWithoutIssuer(keyPair)));
	}

	@Test
	public void subject_ofProperJwt_shouldBeExpectedSubject() {
		assertEquals(Val.of(JWTTestUtility.subject), jwtLibraryService.subject(JWTTestUtility.jwt(keyPair)));
	}

	@Test
	public void subject_ofJwtWithoutSubject_shouldBeUndefined() {
		assertEquals(Val.UNDEFINED, jwtLibraryService.subject(JWTTestUtility.jwtWithoutSubject(keyPair)));
	}

	@Test
	public void authorities_ofProperJwt_shouldBeExpectedAuthorities() {
		assertEquals(Val.of(JsonTestUtility.jsonNode(JWTTestUtility.authorities)),
				jwtLibraryService.authorities(JWTTestUtility.jwt(keyPair)));
	}

	@Test
	public void authorities_ofJwtWithoutAuthorities_shouldBeEmpty() {
		assertEquals(Val.ofEmptyArray(), jwtLibraryService.authorities(JWTTestUtility.jwtWithoutAuthorities(keyPair)));
	}

	@Test
	public void payload_ofNull_shouldBeEmpty() {
		assertEquals(Val.ofEmptyObject(), jwtLibraryService.payload(null));
	}

	@Test
	public void payload_ofproperJwt_shouldBeExpectedPayload()
			throws ParseException, JsonMappingException, JsonProcessingException {
		Val jwt = JWTTestUtility.jwt(keyPair);
		Payload payload = SignedJWT.parse(jwt.getText()).getPayload();
		JsonNode jsonNode = JsonTestUtility.jsonNode(payload.toString());
		assertEquals(jsonNode, jwtLibraryService.payload(jwt).getJsonNode());
	}

}
