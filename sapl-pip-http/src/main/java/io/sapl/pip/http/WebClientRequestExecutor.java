package io.sapl.pip.http;

import static io.sapl.pip.http.RequestSpecification.HTTP_GET;
import static io.sapl.pip.http.RequestSpecification.HTTP_POST;

import java.net.MalformedURLException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pip.AttributeException;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class WebClientRequestExecutor {

	private static final String BAD_URL_INFORMATION = "Bad URL information.";
	private static final String NO_URL_PROVIDED = "No URL provided.";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static Flux<JsonNode> executeRequest(RequestSpecification saplRequest, String requestType) throws AttributeException {
		try {
			final URLSpecification urlSpec = getURLSpecification(saplRequest);
			final WebClient webClient = WebClient.create(urlSpec.baseUrl());
			if (HTTP_GET.equals(requestType)) {
				return webClient.get()
						.uri(urlSpec.pathAndQueryString())
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else if (HTTP_POST.equals(requestType)) {
				return webClient.post()
						.uri(urlSpec.pathAndQueryString())
						.contentType(MediaType.APPLICATION_JSON_UTF8)
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.headers(httpHeaders -> addHeaders(httpHeaders, saplRequest))
						.syncBody(getBody(saplRequest))
						.retrieve()
						.bodyToFlux(JsonNode.class);
			} else {
				throw new AttributeException("Unsupported request method " + requestType);
			}
		} catch (JsonProcessingException e) {
			throw new AttributeException(e);
		}
	}

	private static URLSpecification getURLSpecification(RequestSpecification saplRequest) throws AttributeException {
		final JsonNode url = saplRequest.getUrl();
		if (url == null) {
			throw new AttributeException(NO_URL_PROVIDED);
		} else if (url.isTextual()) {
			final String urlStr = url.asText();
			try {
				return URLSpecification.from(urlStr);
			}
			catch (MalformedURLException e) {
				throw new AttributeException(BAD_URL_INFORMATION, e);
			}
		} else if (url.isObject()) {
			try {
				return MAPPER.treeToValue(url, URLSpecification.class);
			} catch (JsonProcessingException e) {
				throw new AttributeException(BAD_URL_INFORMATION, e);
			}
		} else {
			throw new AttributeException(BAD_URL_INFORMATION);
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
