/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt.apikey;

import java.nio.charset.StandardCharsets;

import org.springframework.security.core.Authentication;
import org.springframework.security.rsocket.api.PayloadExchange;
import org.springframework.security.rsocket.authentication.PayloadExchangeAuthenticationConverter;
import org.springframework.util.MimeType;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.sapl.server.lt.SAPLServerLTProperties;
import reactor.core.publisher.Mono;

public class ApiKeyPayloadExchangeAuthenticationConverter implements PayloadExchangeAuthenticationConverter {
	private final SAPLServerLTProperties pdpProperties;
	private final String                 apiKeyMimeTypeValue;

	public ApiKeyPayloadExchangeAuthenticationConverter(SAPLServerLTProperties pdpProperties) {
		this.pdpProperties       = pdpProperties;
		this.apiKeyMimeTypeValue = String.valueOf(MimeType.valueOf("messaging/" + pdpProperties.getApiKeyHeaderName()));
	}

	@Override
	public Mono<Authentication> convert(PayloadExchange exchange) {
		ByteBuf           metadata          = exchange.getPayload().metadata();
		CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);
		for (CompositeMetadata.Entry entry : compositeMetadata) {
			if (apiKeyMimeTypeValue.equals(entry.getMimeType())) {
				String apikey = entry.getContent().toString(StandardCharsets.UTF_8);
				if (pdpProperties.getAllowedApiKeys().contains(apikey)) {
					return Mono.just(new ApiKeyAuthenticationToken(apikey, "apikey"));
				} else {
					return Mono.error(() -> new ApiKeyAuthenticationException("ApiKey not authorized"));
				}
			}
		}
		return Mono.empty();
	}
}
