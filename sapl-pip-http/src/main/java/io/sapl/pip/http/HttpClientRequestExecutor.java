package io.sapl.pip.http;

import static io.sapl.pip.http.RequestSpecification.HTTP_DELETE;
import static io.sapl.pip.http.RequestSpecification.HTTP_GET;
import static io.sapl.pip.http.RequestSpecification.HTTP_PATCH;
import static io.sapl.pip.http.RequestSpecification.HTTP_POST;
import static io.sapl.pip.http.RequestSpecification.HTTP_PUT;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.AttributeException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HttpClientRequestExecutor {

	private static final String INVALID_HTTP_ACTION = "Requested action is invalid.";
	private static final String BAD_URL_INFORMATION = "Bad URL information.";
	private static final String NO_URL_PROVIDED = "No URL provided.";
	private static final String JSON_BODY_PROCESSING_ERROR = "JSON body processing error.";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static JsonNode executeRequest(RequestSpecification saplRequest, String requestType) throws AttributeException {
		final HttpUriRequest request = HttpUriRequestFactory.buildHttpUriRequest(saplRequest, requestType);
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			return getHttpResponseAndConvert(request, httpClient);
		} catch (IOException e) {
			throw new AttributeException(e);
		}
	}

	private static JsonNode getHttpResponseAndConvert(HttpUriRequest request, CloseableHttpClient httpClient) throws IOException {
		try (CloseableHttpResponse response = httpClient.execute(request)) {
			final String content = convertStreamToString(response.getEntity().getContent());
			try {
				return MAPPER.readValue(content, JsonNode.class);
			} catch (IOException e) {
				return JSON.textNode(content);
			}
		}
	}

	private static String convertStreamToString(java.io.InputStream inputStream) {
		try (Scanner s = new Scanner(inputStream, StandardCharsets.UTF_8.toString())) {
			s.useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
	}


	static class HttpUriRequestFactory {

		static HttpUriRequest buildHttpUriRequest(RequestSpecification saplRequest, String requestType) throws AttributeException {
			HttpUriRequest request;

			final String requestUrl = buildUrl(saplRequest);
			if (HTTP_GET.equals(requestType)) {
				request = new HttpGet(requestUrl);
			} else if (HTTP_POST.equals(requestType)) {
				request = new HttpPost(requestUrl);
				HttpEntity entity = buildEntity(saplRequest);
				if (entity != null) {
					((HttpPost) request).setEntity(entity);
				}
			} else if (HTTP_PUT.equals(requestType)) {
				request = new HttpPut(requestUrl);
				HttpEntity entity = buildEntity(saplRequest);

				if (entity != null) {
					((HttpPut) request).setEntity(entity);
				}
			} else if (HTTP_PATCH.equals(requestType)) {
				request = new HttpPatch(requestUrl);
				HttpEntity entity = buildEntity(saplRequest);
				if (entity != null) {
					((HttpPatch) request).setEntity(entity);
				}
			} else if (HTTP_DELETE.equals(requestType)) {
				request = new HttpDelete(requestUrl);
			} else {
				throw new AttributeException(INVALID_HTTP_ACTION);
			}

			if (saplRequest.getHeaders() != null) {
				for (Map.Entry<String, String> entry : saplRequest.getHeaders().entrySet()) {
					request.addHeader(entry.getKey(), entry.getValue());
				}
			}

			return request;
		}

		private static String buildUrl(RequestSpecification saplRequest) throws AttributeException {
			String result;

			final JsonNode url = saplRequest.getUrl();
			if (url == null) {
				throw new AttributeException(NO_URL_PROVIDED);
			} else if (url.isTextual()) {
				result = url.asText();
			} else if (url.isObject()) {
				try {
					URLSpecification urlSpec = MAPPER.treeToValue(url, URLSpecification.class);
					result = urlSpec.asString();
				} catch (JsonProcessingException e) {
					throw new AttributeException(BAD_URL_INFORMATION, e);
				}
			} else {
				throw new AttributeException(BAD_URL_INFORMATION);
			}

			return result;
		}

		private static HttpEntity buildEntity(RequestSpecification saplRequest) throws AttributeException {
			if (saplRequest.getBody() != null) {
				try {
					return new StringEntity(MAPPER.writeValueAsString(saplRequest.getBody()));
				} catch (UnsupportedEncodingException | JsonProcessingException e) {
					throw new AttributeException(JSON_BODY_PROCESSING_ERROR, e);
				}
			} else if (saplRequest.getRawBody() != null) {
				return new ByteArrayEntity(saplRequest.getRawBody().getBytes(StandardCharsets.UTF_8));
			} else {
				return null;
			}
		}

	}
}
