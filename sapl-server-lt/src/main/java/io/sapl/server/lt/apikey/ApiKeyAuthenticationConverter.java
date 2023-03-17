package io.sapl.server.lt.apikey;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.server.lt.SAPLServerLTProperties;
import reactor.core.publisher.Mono;

public class ApiKeyAuthenticationConverter implements ServerAuthenticationConverter {
	private final SAPLServerLTProperties pdpProperties;

	public ApiKeyAuthenticationConverter(SAPLServerLTProperties pdpProperties) {
		this.pdpProperties = pdpProperties;
	}

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		return Mono.justOrEmpty(exchange)
				.flatMap(serverWebExchange -> Mono.justOrEmpty(
						serverWebExchange.getRequest().getHeaders().get(pdpProperties.getApiKeyHeaderName())))
				.filter(headerValues -> !headerValues.isEmpty()).flatMap(headerValues -> lookup(headerValues.get(0)));
	}

	/**
	 * Lookup authentication token in cache.
	 *
	 * @param apiKey api key
	 */
	private Mono<ApiKeyAuthenticationToken> lookup(final String apiKey) {
		if (pdpProperties.getAllowedApiKeys().contains(apiKey)) {
			return Mono.just(new ApiKeyAuthenticationToken(apiKey, "apikey"));
		} else {
			return Mono.error(() -> new ApiKeyAuthenticationException("ApiKey not authorized"));
		}
	}

}