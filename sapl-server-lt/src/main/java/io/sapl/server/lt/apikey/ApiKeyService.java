package io.sapl.server.lt.apikey;

import io.netty.buffer.ByteBuf;
import io.rsocket.metadata.CompositeMetadata;
import io.sapl.server.lt.SAPLServerLTProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.rsocket.authentication.PayloadExchangeAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final SAPLServerLTProperties pdpProperties;
    private final PasswordEncoder passwordEncoder;
    private final String rsocketApiKeyMimeTypeValue = String.valueOf(MimeType.valueOf("messaging/Bearer"));
    private final CacheManager cacheManager;

    /**
     * Lookup authentication token in cache.
     *
     * @param apiKey api key
     */
    private Mono<Authentication> checkApiKey(final String apiKey) {
        var cache = cacheManager.getCache("ApiKeyCache");
        var cacheEntry = cache.get(apiKey);
        if (cacheEntry != null){
            return Mono.just(new ApiKeyAuthenticationToken((String) cacheEntry.get()));
        } else {
            for (var encodedApiKey : pdpProperties.getAllowedApiKeys()) {
                log.debug("checking ApiKey against encoded ApiKey: " + encodedApiKey);
                if (passwordEncoder.matches(apiKey, encodedApiKey)) {
                    cache.put(apiKey, encodedApiKey);
                    return Mono.just(new ApiKeyAuthenticationToken(encodedApiKey));
                }
            }
        }
        return Mono.error(() -> new ApiKeyAuthenticationException("ApiKey not authorized"));
    }

    public ServerAuthenticationConverter getHttpApiKeyAuthenticationConverter() {
        return exchange -> Mono.justOrEmpty(exchange)
                .flatMap(serverWebExchange -> Mono
                        .justOrEmpty(serverWebExchange.getRequest().getHeaders().get("Authorization")))
                        .map(headerValues -> headerValues
                        .stream()
                        .filter(x -> x.startsWith("Bearer sapl_"))
                        .map(x -> x.replaceFirst("^Bearer ", ""))
                        .findFirst())
                .filter(Optional::isPresent)
                .flatMap(apiKey -> checkApiKey(apiKey.get()));
    }

    public PayloadExchangeAuthenticationConverter getRsocketApiKeyAuthenticationConverter() {
        return exchange -> {
            ByteBuf metadata = exchange.getPayload().metadata();
            CompositeMetadata compositeMetadata = new CompositeMetadata(metadata, false);
            for (CompositeMetadata.Entry entry : compositeMetadata) {
                if (rsocketApiKeyMimeTypeValue.equals(entry.getMimeType())) {
                    String apikey = entry.getContent().toString(StandardCharsets.UTF_8);
                    return checkApiKey(apikey);
                }
            }
            return Mono.empty();
        };

    }

}
