package io.sapl.webclient;

import static io.sapl.webclient.URLSpecification.HTTPS_SCHEME;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpClient;

public class WebClientRequestExecutor {

	private static final String BAD_URL_INFORMATION = "Bad URL information.";
	private static final String NO_URL_PROVIDED = "No URL provided.";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public Flux<JsonNode> executeReactiveRequest(RequestSpecification saplRequest, HttpMethod httpMethod) {
		try {
			final URLSpecification urlSpec = getURLSpecification(saplRequest);
			final WebClient webClient = createWebClient(urlSpec.baseUrl());
			if (httpMethod == GET) {
				return webClient.get()
						.uri(urlSpec.pathAndQueryString())
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == POST) {
				return webClient.post()
						.uri(urlSpec.pathAndQueryString())
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.syncBody(getBody(saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == PUT) {
				return webClient.put()
						.uri(urlSpec.pathAndQueryString())
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.syncBody(getBody(saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == DELETE) {
				return webClient.delete()
						.uri(urlSpec.pathAndQueryString())
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (httpMethod == PATCH) {
				return webClient.patch()
						.uri(urlSpec.pathAndQueryString())
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.syncBody(getBody(saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else {
				return Flux.error(new IOException("Unsupported request method " + httpMethod));
			}
		} catch (Exception e) {
			return Flux.error(e);
		}
	}

	public JsonNode executeBlockingRequest(RequestSpecification saplRequest, HttpMethod httpMethod) throws IOException {
		final URLSpecification urlSpec = getURLSpecification(saplRequest);
		final WebClient webClient = createWebClient(urlSpec.baseUrl());
		if (httpMethod == GET) {
			final ClientResponse response = webClient.get()
					.uri(urlSpec.pathAndQueryString())
					.accept(MediaType.APPLICATION_JSON)
					.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
					.exchange()
					.block();
			if (response.statusCode().is2xxSuccessful()) {
				final Mono<JsonNode> body = response.body(BodyExtractors.toMono(JsonNode.class));
				return body.block();
			} else {
				throw new IOException("HTTP GET request returned with status code " + response.statusCode().value());
			}
		} else if (httpMethod == POST) {
			final ClientResponse response = webClient.post()
					.uri(urlSpec.pathAndQueryString())
					.contentType(MediaType.APPLICATION_JSON_UTF8)
					.accept(MediaType.APPLICATION_JSON)
					.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
					.syncBody(getBody(saplRequest))
					.exchange()
					.block();
			if (response.statusCode().is2xxSuccessful()) {
				final Mono<JsonNode> body = response.body(BodyExtractors.toMono(JsonNode.class));
				return body.block();
			} else {
				throw new IOException("HTTP POST request returned with status code " + response.statusCode().value());
			}
		} else if (httpMethod == PUT) {
			final ClientResponse response = webClient.put()
					.uri(urlSpec.pathAndQueryString())
					.contentType(MediaType.APPLICATION_JSON_UTF8)
					.accept(MediaType.APPLICATION_JSON)
					.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
					.syncBody(getBody(saplRequest))
					.exchange()
					.block();
			if (response.statusCode().is2xxSuccessful()) {
				final Mono<JsonNode> body = response.body(BodyExtractors.toMono(JsonNode.class));
				return body.block();
			} else {
				throw new IOException("HTTP PUT request returned with status code " + response.statusCode().value());
			}
		} else if (httpMethod == DELETE) {
			final ClientResponse response = webClient.delete()
					.uri(urlSpec.pathAndQueryString())
					.accept(MediaType.APPLICATION_JSON)
					.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
					.exchange()
					.block();
			if (response.statusCode().is2xxSuccessful()) {
				final Mono<JsonNode> body = response.body(BodyExtractors.toMono(JsonNode.class));
				return body.block();
			} else {
				throw new IOException("HTTP DELETE request returned with status code " + response.statusCode().value());
			}
		} else if (httpMethod == PATCH) {
			final ClientResponse response = webClient.patch()
					.uri(urlSpec.pathAndQueryString())
					.contentType(MediaType.APPLICATION_JSON_UTF8)
					.accept(MediaType.APPLICATION_JSON)
					.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
					.syncBody(getBody(saplRequest))
					.exchange()
					.block();
			if (response.statusCode().is2xxSuccessful()) {
				final Mono<JsonNode> body = response.body(BodyExtractors.toMono(JsonNode.class));
				return body.block();
			} else {
				throw new IOException("HTTP PATCH request returned with status code " + response.statusCode().value());
			}
		} else {
			throw new IOException("Unsupported request method " + httpMethod);
		}
	}

	private WebClient createWebClient(String baseUrl) throws IOException {
		if (baseUrl.startsWith(HTTPS_SCHEME)) {
			final SslContext sslContext = createSslContext();
			final SslProvider sslProvider = SslProvider.builder().sslContext(sslContext).build();
			final TcpClient tcpClient = TcpClient.create().secure(sslProvider);
			final HttpClient httpClient = HttpClient.from(tcpClient);
			final ClientHttpConnector httpConnector = new ReactorClientHttpConnector(httpClient);
			return WebClient.builder()
					.clientConnector(httpConnector)
					.baseUrl(baseUrl)
					.build();
		}
		return WebClient.create(baseUrl);
	}

	private SslContext createSslContext() throws IOException {
		// return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
		try {
			final KeyStore ks = KeyStore.getInstance("PKCS12");
			try (InputStream is = getClass().getResourceAsStream("/truststore.p12")) {
				ks.load(is, "localhostpassword".toCharArray());
			}

			final List<String> aliases = Collections.list(ks.aliases());
			final X509Certificate[] trusted = aliases.stream().map(alias -> {
				try {
					return (X509Certificate) ks.getCertificate(alias);
				} catch (KeyStoreException e) {
					throw new RuntimeException(e);
				}
			}).toArray(X509Certificate[]::new);

			return SslContextBuilder.forClient().trustManager(trusted).build();
		} catch (RuntimeException e) {
			throw new SSLException(e.getCause() instanceof KeyStoreException ? e.getCause() : e);
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	private static URLSpecification getURLSpecification(RequestSpecification saplRequest) throws IOException {
		final JsonNode url = saplRequest.getUrl();
		if (url == null) {
			throw new IOException(NO_URL_PROVIDED);
		} else if (url.isTextual()) {
			final String urlStr = url.asText();
			try {
				return URLSpecification.from(urlStr);
			}
			catch (MalformedURLException e) {
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

	private static void addHeaders(HttpHeaders httpHeaders, RequestSpecification saplRequest) {
		final Map<String, String> reqHeaders = saplRequest.getHeaders();
		if (reqHeaders != null) {
			for (Map.Entry<String, String> header : reqHeaders.entrySet()) {
				httpHeaders.set(header.getKey(), header.getValue());
			}
		}
	}

	private static Object getBody(RequestSpecification saplRequest) throws JsonProcessingException {
		if (saplRequest.getBody() != null) {
			return MAPPER.writeValueAsString(saplRequest.getBody());
		} else if (saplRequest.getRawBody() != null) {
			return MAPPER.writeValueAsBytes(saplRequest.getRawBody());
		} else {
			return "";
		}
	}
}
