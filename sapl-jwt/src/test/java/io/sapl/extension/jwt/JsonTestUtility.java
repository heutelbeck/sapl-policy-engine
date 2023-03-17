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

import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.experimental.UtilityClass;
import okhttp3.mockwebserver.MockWebServer;

@UtilityClass
class JsonTestUtility {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * @return JsonNode created from source object
	 */
	static JsonNode jsonNode(Object source) {

		// first try if source is a Json String
		if (source instanceof String) {
			try {
				return MAPPER.readTree((String) source);
			} catch (Exception e) {
				// expected exception
			}
		}

		// if that failed, convert source object to JsonNode
		return MAPPER.valueToTree(source);
	}

	/**
	 * @param kid1     the ID of the first KeyPair
	 * @param kid2     the ID of the second KeyPair
	 * @param keyPair1 KeyPair of the first public key. Non textual, if null
	 * @param keyPair2 KeyPair of the second public key. Bogus, if null
	 * @return whitelist variables containing two public keys
	 */
	static Map<String, JsonNode> publicKeyWhitelistVariables(String kid1, KeyPair keyPair1, String kid2,
			KeyPair keyPair2) {

		ObjectNode keyNode   = MAPPER.createObjectNode();
		ObjectNode valueNode = MAPPER.createObjectNode();

		if (keyPair1 != null) {
			String encodedFirstKey = Base64.getUrlEncoder().encodeToString(keyPair1.getPublic().getEncoded());
			valueNode.put(kid1, encodedFirstKey);
		} else
			valueNode.putNull(kid1);

		String encodedSecondKey = "This is Bogus";
		if (keyPair2 != null)
			encodedSecondKey = Base64.getUrlEncoder().encodeToString(keyPair2.getPublic().getEncoded());
		valueNode.put(kid2, encodedSecondKey);

		keyNode.set(JWTPolicyInformationPoint.WHITELIST_VARIABLES_KEY, valueNode);
		return Map.of("jwt", keyNode);
	}

	/**
	 * @param server mock web server for automatically generated url, or use null to
	 *               omit
	 * @param method request method ("GET" or "POST"), use null or empty String to
	 *               omit, use "NONETEXT" to generate a none-text value
	 * @return environment variables containing public key server URI and request
	 *         method
	 */
	static Map<String, JsonNode> publicKeyUriVariables(MockWebServer server, String method) {

		ObjectNode keyNode   = MAPPER.createObjectNode();
		ObjectNode valueNode = serverNode(server, method, null);

		keyNode.set(JWTPolicyInformationPoint.PUBLIC_KEY_VARIABLES_KEY, valueNode);
		return Map.of("jwt", keyNode);
	}

	static ObjectNode serverNode(MockWebServer server, String method, Object ttl) {
		ObjectNode valueNode = MAPPER.createObjectNode();

		if (server != null) {
			valueNode.put(JWTKeyProvider.PUBLIC_KEY_URI_KEY, server.url("/") + "public-keys/{id}");
		}
		if (method != null && method.length() > 0) {
			if ("NONETEXT".equals(method)) {
				valueNode.set(JWTKeyProvider.PUBLIC_KEY_METHOD_KEY, jsonNode(Boolean.FALSE));
			} else {
				valueNode.put(JWTKeyProvider.PUBLIC_KEY_METHOD_KEY, method);
			}
		}
		putTTL(valueNode, ttl);
		return valueNode;
	}

	private static void putTTL(ObjectNode valueNode, Object ttl) {
		if (ttl == null)
			return;
		if (ttl instanceof Long)
			valueNode.put(JWTKeyProvider.KEY_CACHING_TTL_MILLIS, ((Long) ttl).longValue());
		else
			valueNode.put(JWTKeyProvider.KEY_CACHING_TTL_MILLIS, ttl.toString());
	}

}
