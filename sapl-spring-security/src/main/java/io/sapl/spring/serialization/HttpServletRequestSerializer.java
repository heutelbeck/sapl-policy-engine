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
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map.Entry;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class HttpServletRequestSerializer extends JsonSerializer<HttpServletRequest> {

	@Override
	public void serialize(HttpServletRequest value, JsonGenerator gen, SerializerProvider serializers)
			throws IOException {
		gen.writeStartObject();
		gen.writeStringField("characterEncoding", value.getCharacterEncoding());
		gen.writeStringField("Content-Type", value.getContentType());
		gen.writeStringField("protocol", value.getProtocol());
		gen.writeStringField("scheme", value.getScheme());
		gen.writeStringField("serverName", value.getServerName());
		gen.writeNumberField("serverPort", value.getServerPort());
		gen.writeStringField("remoteAddress", value.getRemoteAddr());
		gen.writeStringField("remoteHost", value.getRemoteHost());
		gen.writeNumberField("remotePort", value.getRemotePort());
		gen.writeBooleanField("isSecure", value.isSecure());
		gen.writeStringField("localName", value.getLocalName());
		gen.writeStringField("localAddress", value.getLocalAddr());
		gen.writeNumberField("localPort", value.getLocalPort());
		gen.writeStringField("authType", value.getAuthType());
		gen.writeStringField("method", value.getMethod());
		gen.writeStringField("pathInfo", value.getPathInfo());
		gen.writeStringField("pathTranslated", value.getPathTranslated());
		gen.writeStringField("contextPath", value.getContextPath());
		gen.writeStringField("queryString", value.getQueryString());
		gen.writeStringField("requestedSessionId", value.getRequestedSessionId());
		gen.writeStringField("requestedURI", value.getRequestURI());
		gen.writeStringField("requestURL", value.getRequestURL().toString());
		gen.writeStringField("servletPath", value.getServletPath());

		writeHeaders(value, gen);
		writeCookies(value, gen);
		writeLocales(value, gen);
		writeParameters(value, gen);
	}

	private void writeHeaders(HttpServletRequest value, JsonGenerator gen) throws IOException {
		Enumeration<String> headerNames = value.getHeaderNames();
		if (headerNames.hasMoreElements()) {
			gen.writeObjectFieldStart("headers");
			while (headerNames.hasMoreElements()) {
				String name = headerNames.nextElement();
				Enumeration<String> headers = value.getHeaders(name);
				if (headerNames.hasMoreElements()) {
					gen.writeArrayFieldStart(name);
					while (headers.hasMoreElements()) {
						gen.writeString(headers.nextElement());
					}
					gen.writeEndArray();
				}
			}
			gen.writeEndObject();
		}
	}

	private void writeCookies(HttpServletRequest value, JsonGenerator gen) throws IOException {
		if (value.getCookies() != null && value.getCookies().length > 0) {
			gen.writeArrayFieldStart("cookies");
			for (Cookie cookie : value.getCookies()) {
				gen.writeObject(cookie);
			}
			gen.writeEndArray();
		}
	}

	private void writeLocales(HttpServletRequest value, JsonGenerator gen) throws IOException {
		gen.writeStringField("locale", value.getLocale().toString());
		Enumeration<Locale> locales = value.getLocales();
		if (locales.hasMoreElements()) {
			gen.writeArrayFieldStart("locales");
			while (locales.hasMoreElements()) {
				gen.writeString(locales.nextElement().toString());
			}
			gen.writeEndArray();
		}
	}

	private void writeParameters(HttpServletRequest value, JsonGenerator gen) throws IOException {
		gen.writeObjectFieldStart("parameters");
		for (Entry<String, String[]> entry : value.getParameterMap().entrySet()) {
			gen.writeArrayFieldStart(entry.getKey());
			for (String val : entry.getValue()) {
				gen.writeString(val);
			}
			gen.writeEndArray();
		}
		gen.writeEndObject();
	}

}
