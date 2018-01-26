package io.sapl.pip.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.AttributeException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RequestExecutor {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static JsonNode executeUriRequest(RequestSpecification saplRequest, String requestType)
			throws AttributeException {
		HttpUriRequest request = saplRequest.toHttpUriRequest(requestType);
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			return getHttpResponseAndConvert(request, httpclient);
		} catch (IOException e) {
			throw new AttributeException(e);
		}
	}

	private static JsonNode getHttpResponseAndConvert(HttpUriRequest request, CloseableHttpClient httpclient)
			throws IOException {
		try (CloseableHttpResponse response = httpclient.execute(request)) {
			String content = convertStreamToString(response.getEntity().getContent());
			try {
				return MAPPER.readValue(content, JsonNode.class);
			} catch (IOException e) {
				return JSON.textNode(content);
			}
		}
	}

	static String convertStreamToString(java.io.InputStream inputStream) {
		try (Scanner s = new Scanner(inputStream, StandardCharsets.UTF_8.toString())) {
			s.useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
	}
}
