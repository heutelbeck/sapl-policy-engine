/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.webclient;

import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.experimental.UtilityClass;

@UtilityClass
@Deprecated // use WebClientRequestExecutor instead
public class HttpClientRequestExecutor {

	private static final String UNSUPPORTED_HTTP_METHOD = "Requested HTTP method is not supported.";

	private static final String BAD_URL_INFORMATION = "Bad URL information.";

	private static final String NO_URL_PROVIDED = "No URL provided.";

	private static final String JSON_BODY_PROCESSING_ERROR = "JSON body processing error.";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static JsonNode executeRequest(RequestSpecification saplRequest, HttpMethod httpMethod) throws IOException {
		final HttpUriRequest request = HttpUriRequestFactory.buildHttpUriRequest(saplRequest, httpMethod);
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			return getHttpResponseAndConvert(request, httpClient);
		}
	}

	private static JsonNode getHttpResponseAndConvert(HttpUriRequest request, CloseableHttpClient httpClient)
			throws IOException {
		try (CloseableHttpResponse response = httpClient.execute(request)) {
			final String content = convertStreamToString(response.getEntity().getContent());
			try {
				return MAPPER.readValue(content, JsonNode.class);
			}
			catch (IOException e) {
				return JSON.textNode(content);
			}
		}
	}

	private static String convertStreamToString(InputStream inputStream) {
		try (Scanner s = new Scanner(inputStream, StandardCharsets.UTF_8.toString())) {
			s.useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
	}

	public static class HttpUriRequestFactory {

		public static HttpUriRequest buildHttpUriRequest(RequestSpecification saplRequest, HttpMethod httpMethod)
				throws IOException {
			final String requestUrl = buildUrl(saplRequest);

			HttpUriRequest request;
			HttpEntity entity;
			switch (httpMethod) {
			case GET:
				request = new HttpGet(requestUrl);
				break;
			case POST:
				request = new HttpPost(requestUrl);
				entity = buildEntity(saplRequest);
				if (entity != null) {
					((HttpPost) request).setEntity(entity);
				}
				break;
			case PUT:
				request = new HttpPut(requestUrl);
				entity = buildEntity(saplRequest);
				if (entity != null) {
					((HttpPut) request).setEntity(entity);
				}
				break;
			case DELETE:
				request = new HttpDelete(requestUrl);
				break;
			case PATCH:
				request = new HttpPatch(requestUrl);
				entity = buildEntity(saplRequest);
				if (entity != null) {
					((HttpPatch) request).setEntity(entity);
				}
				break;
			default:
				throw new IOException(UNSUPPORTED_HTTP_METHOD);
			}

			if (saplRequest.getHeaders() != null) {
				for (Map.Entry<String, String> entry : saplRequest.getHeaders().entrySet()) {
					request.addHeader(entry.getKey(), entry.getValue());
				}
			}

			return request;
		}

		private static String buildUrl(RequestSpecification saplRequest) throws IOException {
			String result;

			final JsonNode url = saplRequest.getUrl();
			if (url == null) {
				throw new IOException(NO_URL_PROVIDED);
			}
			else if (url.isTextual()) {
				result = url.asText();
			}
			else if (url.isObject()) {
				try {
					final URLSpecification urlSpec = MAPPER.treeToValue(url, URLSpecification.class);
					result = urlSpec.asString();
				}
				catch (JsonProcessingException e) {
					throw new IOException(BAD_URL_INFORMATION, e);
				}
			}
			else {
				throw new IOException(BAD_URL_INFORMATION);
			}

			return result;
		}

		private static HttpEntity buildEntity(RequestSpecification saplRequest) throws IOException {
			if (saplRequest.getBody() != null) {
				try {
					return new StringEntity(MAPPER.writeValueAsString(saplRequest.getBody()));
				}
				catch (UnsupportedEncodingException | JsonProcessingException e) {
					throw new IOException(JSON_BODY_PROCESSING_ERROR, e);
				}
			}
			else if (saplRequest.getRawBody() != null) {
				return new ByteArrayEntity(saplRequest.getRawBody().getBytes(StandardCharsets.UTF_8));
			}
			else {
				return null;
			}
		}

	}

}
