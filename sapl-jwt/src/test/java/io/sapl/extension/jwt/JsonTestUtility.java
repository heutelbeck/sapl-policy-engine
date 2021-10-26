package io.sapl.extension.jwt;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.mockwebserver.MockWebServer;

public class JsonTestUtility {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String MOCK_PEP_OBJECT = "{" + "'element1': 'value1', " + "'element2': {"
			+ "'element21': 'value21', " + "'element22': 'value22'" + "}, " + "'element3': {"
			+ "'element31': 'value31', " + "'element32': {" + "'element321': 'value321', " + "'HEADER': ["
			+ "'value3221', " + "'SCHEME', " + "'value3223'" + "]," + "'element323': {" + "'element3231': 'value3231', "
			+ "'AUTHKEY': 'AUTHDATA'" + "}" + "}, " + "'element33': 'value33'" + "}, " + "'element4': 'value4'" + "}";

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
	 * @param header key to use for http header
	 * @param scheme key to use for http authorization scheme
	 * @return JsonNode to simulate a policy enforcement point action or resource object
	 */
	static JsonNode getPepResource(String header, String scheme) {
		return jsonNode(MOCK_PEP_OBJECT.replace("HEADER", header).replace("SCHEME", scheme).replaceAll("'", "\""));
	}

	/**
	 * @param authKey key for credentials
	 * @param authData credentials data
	 * @return JsonNode to simulate a policy enforcement point subject
	 */
	public static JsonNode getPepSubject(String authKey, String authData) {
		return jsonNode(
				MOCK_PEP_OBJECT.replace("AUTHKEY", authKey).replace("AUTHDATA", authData).replaceAll("'", "\""));
	}

	/**
	 * @return an object mapper
	 */
	static ObjectMapper getMapper() {
		return MAPPER;
	}

	/**
	 * @param server mock web server for automatically generated url, or use null to omit
	 * @param method request method ("GET" or "POST"), use null or empty String to omit,
	 * use "NONETEXT" to generate a none-text value
	 * @return environment variables containing public key server URI and request method
	 */
	static Map<String, JsonNode> publicKeyUriVariables(MockWebServer server, String method) {
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

		keyNode.set(JWTPolicyInformationPoint.PUBLICKEY_VARIABLES_KEY, valueNode);

		return MAPPER.convertValue(keyNode, new TypeReference<Map<String, JsonNode>>() {
		});
	}

}
