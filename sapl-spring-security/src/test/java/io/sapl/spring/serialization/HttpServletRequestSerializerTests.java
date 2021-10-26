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
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBoolean;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;

class HttpServletRequestSerializerTests {

	private ObjectMapper mapper = new ObjectMapper();

	private JsonNode serialize(HttpServletRequest invocation) throws IOException {
		TokenBuffer jsonGenerator = new TokenBuffer(mapper, false);
		SerializerProvider serializerProvider = mapper.getSerializerProvider();
		new HttpServletRequestSerializer().serialize(invocation, jsonGenerator, serializerProvider);
		jsonGenerator.flush();
		return (JsonNode) new ObjectMapper().readTree(jsonGenerator.asParser());
	}

	@Test
	void whenProtocolSet_thenItIsTheSameInJson() throws IOException {
		var expected = "HTTP/2";
		var request = new MockHttpServletRequest();
		request.setProtocol(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.PROTOCOL, is(jsonText(expected)))));
	}

	@Test
	void whenParametersSet_thenItIsTheSameInJson() throws IOException {
		var request = new MockHttpServletRequest();
		request.setParameter("key1", "value1a", "value1b");
		request.setParameter("key2", "value2");
		var result = serialize(request);
		assertThat(
				result, is(
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
		var request = new MockHttpServletRequest();
		request.setCookies(new Cookie("name1", "value1"), new Cookie("name2", "value2"));
		var result = serialize(request);
		assertThat(result,
				is(jsonObject().where(HttpServletRequestSerializer.COOKIES, is(jsonArray(containsInAnyOrder(
						jsonObject().where("name", is(jsonText("name1"))).where("value", is(jsonText("value1"))),
						jsonObject().where("name", is(jsonText("name2"))).where("value", is(jsonText("value2")))))))));
	}

	@Test
	void whenHeadersSet_thenItIsTheSameInJson() throws IOException {
		var request = new MockHttpServletRequest();
		request.addHeader("header1", new String[] { "value1a", "value1b" });
		request.addHeader("header2", "value2");
		// attention: the behavior of MockHttpServletRequest is odd
		// one header is only added for real if another one is added afterwards
		// here header 2 adds header 1 and header 3 adds header 2
		// and header 3 is never really added
		request.addHeader("header3", "value2");
		var result = serialize(request);
		assertThat(
				result, is(
						jsonObject()
								.where(HttpServletRequestSerializer.HEADERS,
										is(jsonObject()
												.where("header1",
														jsonArray(containsInAnyOrder(jsonText("value1a"),
																jsonText("value1b"))))
												.where("header2",
														jsonArray(containsInAnyOrder(jsonText("value2"))))))));
	}

	@Test
	void whenServerNameSet_thenItIsTheSameInJson() throws IOException {
		var expected = "sapl.io";
		var request = new MockHttpServletRequest();
		request.setServerName(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.SERVER_NAME, is(jsonText(expected)))));
	}

	@Test
	void whenServerPortSet_thenItIsTheSameInJson() throws IOException {
		var expected = 443;
		var request = new MockHttpServletRequest();
		request.setServerPort(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.SERVER_PORT, is(jsonInt(expected)))));
	}

	@Test
	void whenRemoteAddressSet_thenItIsTheSameInJson() throws IOException {
		var expected = "123.022.233.121";
		var request = new MockHttpServletRequest();
		request.setRemoteAddr(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_ADDRESS, is(jsonText(expected)))));
	}

	@Test
	void whenRemotePortSet_thenItIsTheSameInJson() throws IOException {
		var expected = 8443;
		var request = new MockHttpServletRequest();
		request.setRemotePort(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_PORT, is(jsonInt(expected)))));
	}

	@Test
	void whenRemoteHostSet_thenItIsTheSameInJson() throws IOException {
		var expected = "demo.sapl.io";
		var request = new MockHttpServletRequest();
		request.setRemoteHost(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_HOST, is(jsonText(expected)))));
	}

	@Test
	void whenSecureSet_thenItIsTheSameInJson() throws IOException {
		var expected = true;
		var request = new MockHttpServletRequest();
		request.setSecure(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.IS_SECURE, is(jsonBoolean(expected)))));
	}

	@Test
	void whenLocalNameSet_thenItIsTheSameInJson() throws IOException {
		var expected = "localhostname";
		var request = new MockHttpServletRequest();
		request.setLocalName(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_NAME, is(jsonText(expected)))));
	}

	@Test
	void whenLocalAddressSet_thenItIsTheSameInJson() throws IOException {
		var expected = "98.99.100.1";
		var request = new MockHttpServletRequest();
		request.setLocalAddr(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_ADDRESS, is(jsonText(expected)))));
	}

	@Test
	void whenLocalPortSet_thenItIsTheSameInJson() throws IOException {
		var expected = 8083;
		var request = new MockHttpServletRequest();
		request.setLocalPort(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_PORT, is(jsonInt(expected)))));
	}

	@Test
	void whenMethodNameSet_thenItIsTheSameInJson() throws IOException {
		var expected = "GET";
		var request = new MockHttpServletRequest();
		request.setMethod(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.METHOD, is(jsonText(expected)))));
	}

	@Test
	void whenContextPathSet_thenItIsTheSameInJson() throws IOException {
		var expected = "/a/b/c";
		var request = new MockHttpServletRequest();
		request.setContextPath(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.CONTEXT_PATH, is(jsonText(expected)))));
	}

	@Test
	void whenEncodingSet_thenItIsTheSameInJson() throws IOException {
		var expected = StandardCharsets.US_ASCII.toString();
		var request = new MockHttpServletRequest();
		request.setCharacterEncoding(expected);
		var result = serialize(request);
		assertThat(result,
				is(jsonObject().where(HttpServletRequestSerializer.CHARACTER_ENCODING, is(jsonText(expected)))));
	}

	@Test
	void whenContentTypeSet_thenItIsTheSameInJson() throws IOException {
		var expected = MediaType.APPLICATION_JSON_VALUE;
		var request = new MockHttpServletRequest();
		request.setContentType(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.CONTENT_TYPE, is(jsonText(expected)))));
	}

	@Test
	void whenAuthTypeSet_thenItIsTheSameInJson() throws IOException {
		var expected = "basic";
		var request = new MockHttpServletRequest();
		request.setAuthType(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.AUTH_TYPE, is(jsonText(expected)))));
	}

	@Test
	void whenQuerySet_thenItIsTheSameInJson() throws IOException {
		var expected = "a=b";
		var request = new MockHttpServletRequest();
		request.setQueryString(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.QUERY_STRING, is(jsonText(expected)))));
	}

	@Test
	void whenSessionIdSet_thenItIsTheSameInJson() throws IOException {
		var expected = "0xaaabbbd1233425fff";
		var request = new MockHttpServletRequest();
		request.setRequestedSessionId(expected);
		var result = serialize(request);
		assertThat(result,
				is(jsonObject().where(HttpServletRequestSerializer.REQUESTED_SESSION_ID, is(jsonText(expected)))));
	}

	@Test
	void whenRequestedUriIsSet_thenItIsTheSameInJson() throws IOException {
		var expected = "https://localhorst";
		var request = new MockHttpServletRequest();
		request.setRequestURI(expected);
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.REQUESTED_URI, is(jsonText(expected)))));
	}

	@Test
	void whenRequestUrlIsSet_thenItIsTheSameInJson() throws IOException {
		var expected = "http://localhost";
		var request = new MockHttpServletRequest();
		var result = serialize(request);
		assertThat(result, is(jsonObject().where(HttpServletRequestSerializer.REQUEST_URL, is(jsonText(expected)))));
	}

	@Test
	void whenLocaleSet_thenItIsTheSameInJson() throws IOException {
		var expected = Locale.GERMAN.toString();
		var request = new MockHttpServletRequest();
		request.setPreferredLocales(List.of(Locale.GERMAN, Locale.UK));
		var result = serialize(request);
		assertThat(result,
				is(jsonObject().where(HttpServletRequestSerializer.LOCALE, is(jsonText(expected))).where(
						HttpServletRequestSerializer.LOCALES,
						is(jsonArray(contains(jsonText(Locale.GERMAN.toString()), jsonText(Locale.UK.toString())))))));
	}

}
