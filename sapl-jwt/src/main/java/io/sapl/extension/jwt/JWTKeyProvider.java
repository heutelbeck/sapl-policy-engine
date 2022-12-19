/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extension.jwt;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class JWTKeyProvider {

	private static final String JWT_KEY_SERVER_HTTP_ERROR = "Error trying to retrieve a public key: ";

	private static final String JWT_KEY_CACHING_ERROR = "The provided caching configuration was not understood: ";

	static final String PUBLIC_KEY_URI_KEY     = "uri";
	static final String PUBLIC_KEY_METHOD_KEY  = "method";
	static final String KEY_CACHING_TTL_MILLIS = "keyCachingTTLmillis";
	static final long   DEFAULT_CACHING_TTL    = 300000L;

	@StandardException
	public static class CachingException extends Exception {

	}

	private final Map<String, RSAPublicKey> keyCache;

	private final Queue<CacheEntry> cachingTimes;

	private final WebClient webClient;

	private long lastTTL = DEFAULT_CACHING_TTL;

	public JWTKeyProvider(WebClient.Builder builder) {
		webClient    = builder.build();
		keyCache     = new ConcurrentHashMap<>();     // HashMap<String,
														// RSAPublicKey>();
		cachingTimes = new ConcurrentLinkedQueue<>(); // PriorityQueue<CacheEntry>();
	}

	public Mono<RSAPublicKey> provide(String kid, JsonNode jPublicKeyServer) throws CachingException {

		var jUri = jPublicKeyServer.get(PUBLIC_KEY_URI_KEY);
		if (jUri == null)
			return Mono.empty();

		var      sMethod = "GET";
		JsonNode jMethod = jPublicKeyServer.get(PUBLIC_KEY_METHOD_KEY);
		if (jMethod != null && jMethod.isTextual())
			sMethod = jMethod.textValue();

		var sUri = jUri.textValue();
		var lTTL = DEFAULT_CACHING_TTL;
		var jTTL = jPublicKeyServer.get(KEY_CACHING_TTL_MILLIS);
		// nested if-statement in order to cover all possible branches during testing
		// (eg. null && canConvertToLong not possible)
		if (jTTL != null)
			if (jTTL.canConvertToLong())
				lTTL = jTTL.longValue();
			else
				throw new CachingException(JWT_KEY_CACHING_ERROR + jTTL);

		setTTLmillis(lTTL);
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

	public void setTTLmillis(long newTTLmillis) {
		lastTTL = newTTLmillis >= 0L ? newTTLmillis : DEFAULT_CACHING_TTL;
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
		final ResponseSpec response;

		// return cached key if present
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
				.map(JWTEncodingDecodingUtils::encodedX509ToRSAPublicKey).filter(Optional::isPresent)
				.map(Optional::get);
	}

	private Mono<? extends Throwable> handleHttpError(ClientResponse response) {
		log.trace(JWT_KEY_SERVER_HTTP_ERROR + response.statusCode());
		return Mono.empty();
	}

	/**
	 * remove all keys from cache, that are older than TTLmillis before now
	 */
	private void pruneCache() {
		var pruneTime   = new Date().toInstant().minusMillis(lastTTL);
		var oldestEntry = cachingTimes.peek();
		while (oldestEntry != null && oldestEntry.wasCachedBefore(pruneTime)) {
			keyCache.remove(oldestEntry.getKeyId());
			cachingTimes.poll();
			oldestEntry = cachingTimes.peek();
		}
	}

	private static class CacheEntry {

		@Getter
		private final String keyId;

		private final Instant cachingTime;

		CacheEntry(String keyId) {
			this.keyId  = keyId;
			cachingTime = new Date().toInstant();
		}

		boolean wasCachedBefore(Instant instant) {
			return cachingTime.isBefore(instant);
		}

	}

}
