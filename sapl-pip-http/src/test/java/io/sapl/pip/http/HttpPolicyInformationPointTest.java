/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpMethod.POST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.net.HttpHeaders;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Flux;

@RunWith(MockitoJUnitRunner.class)
public class HttpPolicyInformationPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private Val actualRequestSpec;

	private JsonNode result;

	private RequestSpecification expectedRequestSpec;

	private WebClientRequestExecutor requestExecutor;

	@Before
	public void init() throws IOException {
		final String request = "{ " + "\"url\": \"http://jsonplaceholder.typicode.com/posts\", " + "\"headers\": { "
				+ "\"" + HttpHeaders.ACCEPT + "\" : \"application/stream+json\", " + "\"" + HttpHeaders.ACCEPT_CHARSET
				+ "\" : \"" + StandardCharsets.UTF_8 + "\" " + "}, " + "\"rawBody\" : \"hello world\" " + "}";

		actualRequestSpec = Val.ofJson(request);
		result = JSON.textNode("result");
		final Map<String, String> headerProperties = new HashMap<>();
		headerProperties.put(HttpHeaders.ACCEPT, "application/stream+json");
		headerProperties.put(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.toString());

		expectedRequestSpec = new RequestSpecification();
		expectedRequestSpec.setUrl(JSON.textNode("http://jsonplaceholder.typicode.com/posts"));
		expectedRequestSpec.setHeaders(headerProperties);
		expectedRequestSpec.setRawBody("hello world");

		requestExecutor = Mockito.spy(WebClientRequestExecutor.class);
		doReturn(Flux.just(result)).when(requestExecutor).executeReactiveRequest(any(RequestSpecification.class),
				any(HttpMethod.class));
	}

	@Test
	public void postRequest() throws AttributeException, IOException {
		var pip = new HttpPolicyInformationPoint(requestExecutor);
		var attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(pip);
		var evaluationCtx = new EvaluationContext(attributeCtx, new AnnotationFunctionContext(), new HashMap<>());
		var returnedAttribute = attributeCtx.evaluate("http.post", actualRequestSpec, evaluationCtx, null).blockFirst();
		assertEquals("return value not matching", Val.of(result), returnedAttribute);
		verify(requestExecutor).executeReactiveRequest(eq(expectedRequestSpec), eq(POST));
	}

}
