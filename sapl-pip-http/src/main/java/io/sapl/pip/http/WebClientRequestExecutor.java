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

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@Slf4j
public class WebClientRequestExecutor {

	private static final String BAD_URL_INFORMATION = "Bad URL information.";
	private static final String NO_URL_PROVIDED = "No URL provided.";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private HttpClient httpClient;

	public WebClientRequestExecutor(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public WebClientRequestExecutor(SslContext sslContext) {
		this(HttpClient.create().secure(spec -> spec.sslContext(sslContext)));
	}

	public WebClientRequestExecutor() {
		this(HttpClient.create().secure());
	}

	public Flux<JsonNode> executeReactiveRequest(RequestSpecification requestSpec, HttpMethod httpMethod) {
		log.debug("Executing {} - {}", httpMethod, requestSpec);
		try {
			final URLSpecification urlSpec = getURLSpecification(requestSpec);
			final WebClient webClient = createWebClient(urlSpec.baseUrl());
			// @formatter:off
			if (httpMethod == GET) {
				return webClient.get().uri(urlSpec.pathAndQueryString()).accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, requestSpec)).retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == POST) {
				return webClient.post().uri(urlSpec.pathAndQueryString()).contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, requestSpec)).bodyValue(getBody(requestSpec))
						.retrieve().bodyToFlux(JsonNode.class);
			} else if (httpMethod == PUT) {
				return webClient.put().uri(urlSpec.pathAndQueryString()).contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, requestSpec)).bodyValue(getBody(requestSpec))
						.retrieve().bodyToFlux(JsonNode.class);
			} else if (httpMethod == DELETE) {
				return webClient.delete().uri(urlSpec.pathAndQueryString()).accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, requestSpec)).retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == PATCH) {
				return webClient.patch().uri(urlSpec.pathAndQueryString()).contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, requestSpec)).bodyValue(getBody(requestSpec))
						.retrieve().bodyToFlux(JsonNode.class);
			}
			// @formatter:on
			else {
				return Flux.error(new IOException("Unsupported request method " + httpMethod));
			}
		} catch (Exception e) {
			return Flux.error(e);
		}
	}

	private WebClient createWebClient(String baseUrl) {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(baseUrl).build();
	}

	private static URLSpecification getURLSpecification(RequestSpecification requestSpec) throws IOException {
		final JsonNode url = requestSpec.getUrl();
		if (url == null) {
			throw new IOException(NO_URL_PROVIDED);
		} else if (url.isTextual()) {
			final String urlStr = url.asText();
			try {
				return URLSpecification.from(urlStr);
			} catch (MalformedURLException e) {
				throw new IOException(BAD_URL_INFORMATION, e);
			}
		} else if (url.isObject()) {
			try {
				return MAPPER.treeToValue(url, URLSpecification.class);
			} catch (JsonProcessingException e) {
				throw new IOException(BAD_URL_INFORMATION, e);
			}
		} else {
			throw new IOException(BAD_URL_INFORMATION);
		}
	}

	private static void addHeaders(HttpHeaders httpHeaders, RequestSpecification requestSpec) {
		final Map<String, String> reqHeaders = requestSpec.getHeaders();
		if (reqHeaders != null) {
			for (Map.Entry<String, String> header : reqHeaders.entrySet()) {
				httpHeaders.set(header.getKey(), header.getValue());
			}
		}
	}

	private static Object getBody(RequestSpecification requestSpec) throws JsonProcessingException {
		if (requestSpec.getBody() != null) {
			return MAPPER.writeValueAsString(requestSpec.getBody());
		} else if (requestSpec.getRawBody() != null) {
			return MAPPER.writeValueAsBytes(requestSpec.getRawBody());
		} else {
			return "";
		}
	}

}
