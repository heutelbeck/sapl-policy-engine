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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;

/**
 * Service class to provide common functionality used by both functions and attributes for
 * evaluating Json Web Tokens
 */
@RequiredArgsConstructor
public class JWTLibraryService {

	static final String HTTP_HEADER_SCHEME = "Bearer";
	static final String AUTHENTICATION_CREDENTIALS_KEY = "credentials";
	static final String AUTHORITIES_KEY = "authorities";
	static final String ISSUER_KEY = "iss";
	static final String SUBJECT_KEY = "sub";

	private final ObjectMapper mapper;

	/**
	 * converts base64 encoded header.body.signature triplet to signed Json Web Token
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return the signed token or null
	 */
	SignedJWT signedJwt(String jwt) {
		try {
			return SignedJWT.parse(jwt);
		}
		catch (ParseException | NullPointerException e) {
			return null;
		}
	}

	/**
	 * extracts header from JWT
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return header or null
	 */
	JWSHeader header(String jwt) {
		try {
			return signedJwt(jwt).getHeader();
		}
		catch (NullPointerException e) {
			return null;
		}
	}

	/**
	 * extracts claims from JWT
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return claims or null
	 */
	JWTClaimsSet claims(String jwt) {
		try {
			return signedJwt(jwt).getJWTClaimsSet();
		}
		catch (ParseException | NullPointerException e) {
			return null;
		}
	}

	/**
	 * tests if a string is a base64 encoded header.body.signature triplet
	 * @param jwt the string to test
	 * @return true if the string could be parsed as a JWT
	 */
	boolean isJwt(String jwt) {
		try {
			SignedJWT.parse(jwt);
			return true;
		}
		catch (ParseException | NullPointerException e) {
			return false;
		}
	}

	/**
	 * Resolves a base64 encoded JWT from the source
	 * @param source object containing JWT
	 * @return JWT as base64 encoded header.body.signature triplet, or
	 * {@code Val.UNDEFINED} if no JWT could be resolved
	 */
	public Val resolveToken(Val source) {

		if (source == null)
			return Val.UNDEFINED;

		String jsonSource = source.getText();

		// first check if the source itself already is a jwt
		String jwt = jsonSource.replaceFirst("^" + HTTP_HEADER_SCHEME + "\\s+", "");
		if (isJwt(jwt)) {
			return Val.of(jwt);
		}

		// then check if the source contains a "credentials" element with a jwt value
		DocumentContext jsonContext = JsonPath.parse(jsonSource);
		String path = "$.." + AUTHENTICATION_CREDENTIALS_KEY;
		List<String> tokens = jsonContext.read(path);
		for (String token : tokens) {
			if (isJwt(token)) {
				return Val.of(token);
			}
		}

		// otherwise check if the source contains a jwt in an http authorization header
		// (first jwt found is returned)
		for (String header : allCases(HttpHeaders.AUTHORIZATION)) {
			path = "$.." + header + "[*]";
			tokens = jsonContext.read(path);
			for (String token : tokens) {
				if (token.startsWith(HTTP_HEADER_SCHEME)) {
					jwt = token.replaceFirst("^" + HTTP_HEADER_SCHEME + "\\s+", "");
					if (isJwt(jwt)) {
						return Val.of(jwt);
					}
				}
			}
		}

		// no jwt could be resolved
		return Val.UNDEFINED;
	}

	/**
	 * converts a string to a list of original, lowercase, and uppercase strings
	 * @param source the string to convert
	 * @return list of three strings in original, lower, and upper case
	 */
	private List<String> allCases(String source) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(source);
		list.add(source.toLowerCase());
		list.add(source.toUpperCase());
		return list;
	}

	/**
	 * Reads a single value from a token
	 * @param token object containing JWT
	 * @param name key of the value to return
	 * @return named value, or {@code Val.UNDEFINED} if the name is not specified
	 */
	public Val value(@Text @JsonObject Val token, @Text Val name) {
		try {
			String jwt = resolveToken(token).getText();
			String result = claims(jwt).getStringClaim(name.getText());
			return Val.of(result);
		}
		catch (ParseException | NullPointerException e) {
			return Val.UNDEFINED;
		}
	}

	/**
	 * Reads a list of values from a token
	 * @param token object containing JWT
	 * @param name key of the values to return
	 * @return named list of values, or {@code Val.UNDEFINED} if the name is not specified
	 */
	public Val values(@Text @JsonObject Val token, @Text Val name) {
		try {
			String jwt = resolveToken(token).getText();
			List<String> result = claims(jwt).getStringListClaim(name.getText());
			if (result == null) {
				return Val.ofEmptyArray();
			}
			JsonNode jsonNode = mapper.convertValue(result, JsonNode.class);
			return Val.of(jsonNode);
		}
		catch (ParseException e) {
			return Val.ofEmptyArray();
		}
	}

	/**
	 * Convenience function to read a token's issuer
	 * @param token object containing JWT
	 * @return the token's issuer, or {@code Val.UNDEFINED} if the issuer is not specified
	 */
	public Val issuer(@Text @JsonObject Val token) {
		return value(token, Val.of(ISSUER_KEY));
	}

	/**
	 * Convenience function to read a token's subject
	 * @param token object containing JWT
	 * @return the token's subject, or {@code Val.UNDEFINED} if the subject is not
	 * specified
	 */
	public Val subject(@Text @JsonObject Val token) {
		return value(token, Val.of(SUBJECT_KEY));
	}

	/**
	 * Convenience function to read a token's authorities
	 * @param token object containing JWT
	 * @return list of granted authorities, or an empty array if no authorities are
	 * specified
	 */
	public Val authorities(@Text @JsonObject Val token) {
		return values(token, Val.of(AUTHORITIES_KEY));
	}

	/**
	 * Provides access to a token's payload as Json node
	 * @param token object containing JWT
	 * @return the payload, or an empty object if the payload could not be parsed
	 */
	public Val payload(@Text @JsonObject Val token) {
		try {
			String jwt = resolveToken(token).getText();
			Payload payload = SignedJWT.parse(jwt).getPayload();
			JsonNode jsonNode = mapper.readTree(payload.toString());
			return Val.of(jsonNode);
		}
		catch (ParseException | JsonProcessingException | NullPointerException e) {
			return Val.ofEmptyObject();
		}
	}

}
