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

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.mockwebserver.MockWebServer;

public class JsonTestUtility {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * @return JsonNode created from source object
	 */
	static JsonNode jsonNode(Object source) {

		// first try if source is a Json String
		if (source instanceof String) {
			try {
				return MAPPER.readTree((String) source);
			}
			catch (Exception e) {
				// expected exception
			}
		}

		// if that failed, convert source object to JsonNode
		return MAPPER.valueToTree(source);
	}

	/**
	 * @param server mock web server for automatically generated url, or use null to omit
	 * @param method request method ("GET" or "POST"), use null or empty String to omit,
	 * use "NONETEXT" to generate a none-text value
	 * @return environment variables containing public key server URI and request method
	 */
	static Map<String, JsonNode> publicKeyUriVariables(MockWebServer server, String method, boolean includePubKeyServer) {
		ObjectNode valueNode = MAPPER.createObjectNode();
		ObjectNode keyNode = MAPPER.createObjectNode();

		if (server != null) {
			valueNode.put(JWTPolicyInformationPoint.PUBLICKEY_URI_KEY, server.url("/").toString() + "public-keys/{id}");
		}

		if (method != null && method.length() > 0) {
			if (method.equals("NONETEXT")) {
				valueNode.set(JWTPolicyInformationPoint.PUBLICKEY_METHOD_KEY, jsonNode(false));
			}
			else {
				valueNode.put(JWTPolicyInformationPoint.PUBLICKEY_METHOD_KEY, method);
			}
		}

		if (includePubKeyServer)
			keyNode.set(JWTPolicyInformationPoint.PUBLICKEY_VARIABLES_KEY, valueNode);

		return Map.of("jwt", keyNode);
		
		//return MAPPER.convertValue(keyNode, new TypeReference<Map<String, JsonNode>>() { });
	}

}
