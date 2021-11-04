package io.sapl.extension.jwt;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class JWTKeyProvider {
	
	private static final String JWT_KEY_SERVER_HTTP_ERROR = "Error trying to retrieve a public key: ";
	
	static final String PUBLICKEY_URI_KEY = "uri";
	static final String PUBLICKEY_METHOD_KEY = "method";
	static final String KEY_CACHING_TTL_MILLIS = "keyCachingTTLmillis";
	static final long DEFAULT_CACHING_TTL = 300000L;

	private final Map<String, RSAPublicKey> keyCache;
	private final Queue<CacheEntry> cachingTimes;

	private final WebClient webClient;
	
	private long lastTTL = DEFAULT_CACHING_TTL;
	
	public JWTKeyProvider(WebClient.Builder builder) {
		webClient = builder.build();
		keyCache = new HashMap<String, RSAPublicKey>();
		cachingTimes = new PriorityQueue<CacheEntry>();
	}
	
	public Mono<RSAPublicKey> provide(String kid, JsonNode jPublicKeyServer) {
		
		var jUri = jPublicKeyServer.get(PUBLICKEY_URI_KEY);
		if (jUri == null)
			return Mono.empty();

		var sMethod = "GET";
		JsonNode jMethod = jPublicKeyServer.get(PUBLICKEY_METHOD_KEY);
		if (jMethod != null && jMethod.isTextual())
			sMethod = jMethod.textValue();

		var sUri = jUri.textValue();
		var lTTL = DEFAULT_CACHING_TTL;
		var jTTL = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
		if (jTTL != null && jTTL.canConvertToLong())
			lTTL = jTTL.longValue();

		lastTTL = lTTL;
		return fetchPublicKey(kid, sUri, sMethod);
	}
	
	public void cache(String kid, RSAPublicKey pubKey) {

		if (isCached(kid))
			return;

		keyCache.put(kid, pubKey);
		cachingTimes.add(new CacheEntry(kid));
	}
	
	public boolean isCached(String kid) {
		pruneCache();
		return keyCache.containsKey(kid);
	}
	
	/**
	 * Fetches public key from remote authentication server
	 * 
	 * @param kid                    ID of public key to fetch
	 * @param publicKeyURI           URI to request the public key
	 * @param publicKeyRequestMethod HTTP request method: GET or POST
	 * @return public key or empty
	 */
	private Mono<RSAPublicKey> fetchPublicKey(String kid, String publicKeyURI, String publicKeyRequestMethod) {
		ResponseSpec response;
		
		//return cached key if present
		if (isCached(kid)) {
			return Mono.just(keyCache.get(kid));
		}
		
		if ("post".equalsIgnoreCase(publicKeyRequestMethod)) {
			// POST request
			response = webClient.post().uri(publicKeyURI, kid).retrieve();
		} else {
			// default GET request
			response = webClient.get().uri(publicKeyURI, kid).retrieve();
		}

		return response.onStatus(HttpStatus::isError, this::handleHttpError).bodyToMono(String.class)
				.map(JWTEncodingDecodingUtils::encodedX509ToRSAPublicKey).filter(Optional::isPresent).map(Optional::get);
	}
	
	private Mono<? extends Throwable> handleHttpError(ClientResponse response) {
		log.trace(JWT_KEY_SERVER_HTTP_ERROR + response.statusCode().toString());
		return Mono.empty();
	}
	
	/**
	 * remove all keys from cache, that are older than TTLmillis before now
	 * @param TTLmillis
	 */
	private void pruneCache() {
		var pruneTime = new Date().toInstant().minusMillis(lastTTL);
		var oldestEntry = cachingTimes.peek();
		while (oldestEntry != null && oldestEntry.wasCachedBefore(pruneTime)) {
			keyCache.remove(oldestEntry.getKeyId());
			cachingTimes.poll();
			oldestEntry = cachingTimes.peek();
		}
	}
	
	private static class CacheEntry implements Comparable<CacheEntry> {
		
		@Getter
		private final String keyId;
		private final Instant cachingTime;
		
		CacheEntry(String keyId) {
			this.keyId = keyId;
			cachingTime = new Date().toInstant();
		}
		
		boolean wasCachedBefore(Instant instant) {
			return cachingTime.isBefore(instant);
		}
		
		@Override
		public String toString() {return "(" + cachingTime.toEpochMilli() + ", " + keyId + ")";}

		@Override
		public int compareTo(CacheEntry o) {
			return cachingTime.compareTo(o.cachingTime);
		}
	}
	
}
