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
package io.sapl.spring.serialization;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;

class ServerHttpRequestSerializerTests {
	private ObjectMapper mapper = new ObjectMapper();

	private JsonNode serialize(ServerHttpRequest invocation) throws IOException {
		TokenBuffer jsonGenerator = new TokenBuffer(mapper, false);
		SerializerProvider serializerProvider = mapper.getSerializerProvider();
		new ServerHttpRequestSerializer().serialize(invocation, jsonGenerator, serializerProvider);
		jsonGenerator.flush();
		return (JsonNode) new ObjectMapper().readTree(jsonGenerator.asParser());
	}

	@Test
	void whenParametersSet_thenItIsTheSameInJson() throws IOException {
		var request = MockServerHttpRequest.get("/foo/bar").queryParam("key1", "value1a", "value1b")
				.queryParam("key2", "value2").build();
		var actual = serialize(request);
		assertThat(
				actual, is(
						jsonObject()
								.where(HttpServletRequestSerializer.PARAMETERS,
										is(jsonObject()
												.where("key1",
														is(jsonArray(
																contains(jsonText("value1a"), jsonText("value1b")))))
												.where("key2", is(jsonArray(contains(jsonText("value2")))))))));
	}

	@Test
	void whenCookiesSet_thenItIsTheSameInJson() throws IOException {
		var request = MockServerHttpRequest.get("/foo/bar")
				.cookie(new HttpCookie("name1", "value1"), new HttpCookie("name2", "value2")).build();
		var actual = serialize(request);
		assertThat(actual,
				is(jsonObject().where(HttpServletRequestSerializer.COOKIES, is(jsonArray(containsInAnyOrder(
						jsonObject().where("name", is(jsonText("name1"))).where("value", is(jsonText("value1"))),
						jsonObject().where("name", is(jsonText("name2"))).where("value", is(jsonText("value2")))))))));
	}

	@Test
	void whenHeadersSet_thenItIsTheSameInJson() throws IOException {
		var request = MockServerHttpRequest.get("/foo/bar").header("header1", new String[] { "value1a", "value1b" })
				.header("header2", "value2").build();
		var actual = serialize(request);
		assertThat(
				actual, is(
						jsonObject()
								.where(HttpServletRequestSerializer.HEADERS,
										is(jsonObject()
												.where("header1",
														jsonArray(containsInAnyOrder(jsonText("value1a"),
																jsonText("value1b"))))
												.where("header2",
														jsonArray(containsInAnyOrder(jsonText("value2"))))))));
	}

//	@Test
//	void whenServerNameSet_thenItIsTheSameInJson() throws IOException {
//		var request = MockServerHttpRequest.get("/foo/bar").remoteAddress(new InetSocketAddress("sapl.io", 443)).build();
//		var expected = "sapl.io";
//		var request = new MockHttpServletRequest();
//		request.setServerName(expected);
//		var actual = serialize(request);
//		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.SERVER_NAME, is(jsonText(expected)))));
//	}
//
//	@Test
//	void whenServerPortSet_thenItIsTheSameInJson() throws IOException {
//		var expected = 443;
//		var request = new MockHttpServletRequest();
//		request.setServerPort(expected);
//		var actual = serialize(request);
//		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.SERVER_PORT, is(jsonInt(expected)))));
//	}

	@Test
	void whenRemoteAddressSet_thenItIsTheSameInJson() throws IOException {
		var expectedIp = "123.22.233.121";
		var expectedPort = 443;
		var request = MockServerHttpRequest.get("/foo/bar")
				.remoteAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_ADDRESS,
				is(jsonText("/" + expectedIp + ":" + expectedPort)))));
	}

//	@Test
//	void whenRemotePortSet_thenItIsTheSameInJson() throws IOException {
//		var expected = 8443;
//		var request = new MockHttpServletRequest();
//		request.setRemotePort(expected);
//		var actual = serialize(request);
//		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_PORT, is(jsonInt(expected)))));
//	}

	@Test
	void whenRemoteHostSet_thenItIsTheSameInJson() throws IOException {
		var expectedHostname = "localhost";
		var request = MockServerHttpRequest.get("/foo/bar").remoteAddress(new InetSocketAddress(expectedHostname, 443))
				.build();
		var actual = serialize(request);
		assertThat(actual,
				is(jsonObject().where(HttpServletRequestSerializer.REMOTE_HOST, is(jsonText(expectedHostname)))));
	}

	@Test
	void whenLocalNameSet_thenItIsTheSameInJson() throws IOException {
		var expectedHostname = "localhost";
		var request = MockServerHttpRequest.get("/foo/bar").localAddress(new InetSocketAddress(expectedHostname, 443))
				.build();
		var actual = serialize(request);
		assertThat(actual,
				is(jsonObject().where(HttpServletRequestSerializer.LOCAL_NAME, is(jsonText(expectedHostname)))));
	}

	@Test
	void whenLocalAddressSet_thenItIsTheSameInJson() throws IOException {
		var expectedIp = "123.22.233.121";
		var expectedPort = 443;
		var request = MockServerHttpRequest.get("/foo/bar")
				.localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_ADDRESS,
				is(jsonText("/" + expectedIp + ":" + expectedPort)))));
	}

	@Test
	void whenLocalPortSet_thenItIsTheSameInJson() throws IOException {
		var expectedIp = "123.22.233.121";
		var expectedPort = 443;
		var request = MockServerHttpRequest.get("/foo/bar")
				.localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_PORT, is(jsonInt(expectedPort)))));
	}

	@Test
	void whenMethodNameSet_thenItIsTheSameInJson() throws IOException {
		var request = MockServerHttpRequest.get("/foo/bar").build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.METHOD, is(jsonText("GET")))));
	}

	@Test
	void whenContextPathSet_thenItIsTheSameInJson() throws IOException {
		var expected = "/a/b/c";
		var request = MockServerHttpRequest.get(expected).build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.CONTEXT_PATH, is(jsonText(expected)))));
	}

	@Test
	void whenRequestedUriIsSet_thenItIsTheSameInJson() throws IOException {
		var expected = "https://localhost";
		var request = MockServerHttpRequest.get(expected).build();
		var actual = serialize(request);
		assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.REQUESTED_URI, is(jsonText(expected)))));
	}

}
