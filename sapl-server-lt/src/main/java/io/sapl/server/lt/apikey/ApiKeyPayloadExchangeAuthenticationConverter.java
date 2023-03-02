package io.sapl.server.lt.apikey;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.sapl.server.lt.SAPLServerLTProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.rsocket.api.PayloadExchange;
import org.springframework.security.rsocket.authentication.PayloadExchangeAuthenticationConverter;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

public class ApiKeyPayloadExchangeAuthenticationConverter implements PayloadExchangeAuthenticationConverter {
    private final SAPLServerLTProperties pdpProperties;
    private final String APIKEY_MIME_TYPE_VALUE;

    public ApiKeyPayloadExchangeAuthenticationConverter(SAPLServerLTProperties pdpProperties) {
        this.pdpProperties = pdpProperties;
        this.APIKEY_MIME_TYPE_VALUE = String.valueOf(MimeType.valueOf("messaging/" + pdpProperties.getApiKeyHeaderName()));
    }

    @Override
    public Mono<Authentication> convert(PayloadExchange exchange) {
        ByteBuf metadata = exchange.getPayload().metadata();
        CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);
        for (CompositeMetadata.Entry entry : compositeMetadata) {
            if (APIKEY_MIME_TYPE_VALUE.equals(entry.getMimeType())) {
                String apikey = entry.getContent().toString(StandardCharsets.UTF_8);
                if (pdpProperties.getAllowedApiKeys().contains(apikey)) {
                    return Mono.just(new ApiKeyAuthenticationToken(apikey, "apikey"));
                } else {
                    return Mono.error(()-> new ApiKeyAuthenticationException("ApiKey not authorized"));
                }
            }
        }
        return Mono.empty();
    }
}
