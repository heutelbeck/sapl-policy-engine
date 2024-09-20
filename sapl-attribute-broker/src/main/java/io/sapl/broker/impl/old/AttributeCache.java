package io.sapl.broker.impl.old;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.sapl.api.interpreter.Val;
import io.sapl.broker.impl.old.EmbeddedAttributeBroker.AttributeSubscriptionKey;
import io.sapl.broker.impl.old.EmbeddedAttributeBroker.CacheEntry;
import reactor.core.publisher.Mono;

public class AttributeCache {

    private final Map<AttributeSubscriptionKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public Mono<Val> lookupCache(AttributeSubscriptionKey key) {
        var hit = cache.computeIfPresent(key, this::updateCache);
        if (hit == null) {
            return Mono.empty();
        }
        return Mono.just(hit.value());
    }

    public void updateCache(AttributeSubscriptionKey key, Val value, Duration ttl) {
        cache.put(key, new CacheEntry(value, Instant.now(), ttl));
    }

    public CacheEntry updateCache(AttributeSubscriptionKey key, CacheEntry hit) {
        if (hit.isExpired()) {
            return null;
        }
        return hit;
    }

    public void garbageCollectCache() {
        cache.forEach((key, entry) -> {
            if (entry.isExpired()) {
                cache.remove(key, entry);
            }
        });
    }

}
