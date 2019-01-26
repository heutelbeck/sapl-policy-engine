/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.pip.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.net.HttpHeaders;

import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpClientRequestExecutor.class)
public class HttpPolicyInformationPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonNode jsonRequestSpec;
	private JsonNode result;
	private RequestSpecification saplPostRequest;

	@Before
	public void init() throws IOException, AttributeException {
		String request =
				"{ " +
					"\"url\": \"http://jsonplaceholder.typicode.com/posts\", " +
					"\"headers\": { " +
						"\"" + HttpHeaders.ACCEPT + "\" : \"application/json\", " +
						"\"" + HttpHeaders.ACCEPT_CHARSET + "\" : \"" + StandardCharsets.UTF_8 + "\" " +
					"}, " +
					"\"rawBody\" : \"hello world\" " +
				"}";

		jsonRequestSpec = MAPPER.readValue(request, JsonNode.class);
		result = JSON.textNode("result");

		Map<String, String> headerProperties = new HashMap<>();
		headerProperties.put(HttpHeaders.ACCEPT, "application/json");
		headerProperties.put(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.toString());

		saplPostRequest = new RequestSpecification();
		saplPostRequest.setUrl(JSON.textNode("http://jsonplaceholder.typicode.com/posts"));
		saplPostRequest.setHeaders(headerProperties);

		PowerMockito.mockStatic(HttpClientRequestExecutor.class);
		when(HttpClientRequestExecutor.executeRequest(any(), any())).thenReturn(result);
	}

	@Test
	public void postRequest() throws AttributeException {
		HttpPolicyInformationPoint pip = new HttpPolicyInformationPoint();
		AnnotationAttributeContext attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(pip);
		Map<String, JsonNode> variables = new HashMap<>();
		JsonNode returnedAttribute = attributeCtx.evaluate("http.post", jsonRequestSpec, variables).blockFirst();

		assertEquals("return value not matching", result, returnedAttribute);
		PowerMockito.verifyStatic(HttpClientRequestExecutor.class, times(1));

		HttpClientRequestExecutor.executeRequest(requestEq(saplPostRequest, RequestSpecification.HTTP_POST),
				eq(RequestSpecification.HTTP_POST));
	}

	private static RequestSpecification requestEq(RequestSpecification expected, String method) throws AttributeException {
		return argThat(new HttpUriRequestMatcher(expected, method));
	}


	private static class HttpUriRequestMatcher implements ArgumentMatcher<RequestSpecification> {

		private final HttpUriRequest expected;
		private final String method;

		HttpUriRequestMatcher(RequestSpecification saplRequest, String requestType) throws AttributeException {
			expected = HttpClientRequestExecutor.HttpUriRequestFactory.buildHttpUriRequest(saplRequest, requestType);
			method = requestType;
		}

		@Override
		public boolean matches(RequestSpecification argument) {
			try {
				HttpUriRequest actual;
				actual = HttpClientRequestExecutor.HttpUriRequestFactory.buildHttpUriRequest(argument, method);
				return headerMatch(actual) && expected.getMethod().equals(actual.getMethod())
						&& expected.getURI().equals(actual.getURI())
						&& expected.getProtocolVersion().equals(actual.getProtocolVersion())
						&& expected.getRequestLine().toString().equals(actual.getRequestLine().toString());
			} catch (AttributeException e) {
				throw new UnsupportedOperationException(e);
			}
		}

		private boolean headerMatch(HttpUriRequest actual) {
			if (expected.getAllHeaders().length != actual.getAllHeaders().length) {
				return false;
			}
			for (Header h : actual.getAllHeaders()) {
				if (!isExpectedHeader(h)) {
					return false;
				}
			}
			return true;
		}

		private boolean isExpectedHeader(Header header) {
			boolean found = false;
			for (Header h : expected.getAllHeaders()) {
				if (headersMatch(h, header)) {
					found = true;
					break;
				}
			}
			return found;
		}

		private boolean headersMatch(Header h1, Header h2) {
			// could be refined to check element equality too
			return h1.getName().equals(h2.getName()) && h1.getValue().equals(h2.getValue());
		}
	}
}
