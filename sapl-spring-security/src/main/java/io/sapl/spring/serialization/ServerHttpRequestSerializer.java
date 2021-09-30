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

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import org.springframework.boot.jackson.JsonComponent;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class ServerHttpRequestSerializer extends JsonSerializer<ServerHttpRequest> {

	static final String PARAMETERS = "parameters";
	static final String LOCALES = "locales";
	static final String LOCALE = "locale";
	static final String COOKIES = "cookies";
	static final String HEADERS = "headers";
	static final String SERVLET_PATH = "servletPath";
	static final String REQUEST_URL = "requestURL";
	static final String REQUESTED_URI = "requestedURI";
	static final String REQUESTED_SESSION_ID = "requestedSessionId";
	static final String QUERY_STRING = "queryString";
	static final String CONTEXT_PATH = "contextPath";
	static final String METHOD = "method";
	static final String AUTH_TYPE = "authType";
	static final String LOCAL_PORT = "localPort";
	static final String LOCAL_ADDRESS = "localAddress";
	static final String LOCAL_NAME = "localName";
	static final String IS_SECURE = "isSecure";
	static final String REMOTE_PORT = "remotePort";
	static final String REMOTE_HOST = "remoteHost";
	static final String REMOTE_ADDRESS = "remoteAddress";
	static final String SERVER_PORT = "serverPort";
	static final String SERVER_NAME = "serverName";
	static final String SCHEME = "scheme";
	static final String PROTOCOL = "protocol";
	static final String CONTENT_TYPE = "Content-Type";
	static final String CHARACTER_ENCODING = "characterEncoding";

	@Override
	public void serialize(ServerHttpRequest value, JsonGenerator gen, SerializerProvider serializers)
			throws IOException {
		gen.writeStartObject();
		gen.writeStringField(SCHEME, value.getURI().getScheme());
		gen.writeStringField(SERVER_NAME, value.getURI().getHost());
		gen.writeNumberField(SERVER_PORT, value.getURI().getPort());
		gen.writeStringField(REMOTE_ADDRESS, value.getRemoteAddress().toString());
		gen.writeStringField(REMOTE_HOST, value.getRemoteAddress().getHostString());
		gen.writeNumberField(REMOTE_PORT, value.getRemoteAddress().getPort());
		gen.writeStringField(LOCAL_NAME, value.getLocalAddress().getHostString());
		gen.writeStringField(LOCAL_ADDRESS, value.getLocalAddress().toString());
		gen.writeNumberField(LOCAL_PORT, value.getLocalAddress().getPort());
		gen.writeStringField(METHOD, value.getMethodValue());
		gen.writeStringField(CONTEXT_PATH, value.getPath().toString());
		gen.writeStringField(REQUESTED_URI, value.getURI().toString());

		writeHeaders(value, gen);
		writeCookies(value, gen);
		writeParameters(value, gen);
		gen.writeEndObject();
	}

	private void writeHeaders(ServerHttpRequest value, JsonGenerator gen) throws IOException {
		var headers = value.getHeaders();
		if (headers.size() == 0)
			return;
		gen.writeObjectFieldStart(HEADERS);
		for (var entry : headers.entrySet()) {
			gen.writeArrayFieldStart(entry.getKey());
			for (var val : entry.getValue())
				gen.writeString(val);
			gen.writeEndArray();
		}
		gen.writeEndObject();
	}

	private void writeCookies(ServerHttpRequest value, JsonGenerator gen) throws IOException {
		MultiValueMap<String, HttpCookie> cookies = value.getCookies();
		if (cookies == null)
			return;

		gen.writeArrayFieldStart(COOKIES);
		for (var entry : cookies.entrySet()) {
			for (var cookie : entry.getValue()) {
				gen.writeStartObject();
				gen.writeObjectField("name", cookie.getName());
				gen.writeObjectField("value", cookie.getValue());
				gen.writeEndObject();
			}
		}
		gen.writeEndArray();
	}

	private void writeParameters(ServerHttpRequest value, JsonGenerator gen) throws IOException {
		if (value.getQueryParams().isEmpty())
			return;

		gen.writeObjectFieldStart(PARAMETERS);
		for (Entry<String, List<String>> entry : value.getQueryParams().entrySet()) {
			gen.writeArrayFieldStart(entry.getKey());
			for (String val : entry.getValue()) {
				gen.writeString(val);
			}
			gen.writeEndArray();
		}
		gen.writeEndObject();
	}

}
