package io.sapl.broker.impl.old;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.sapl.api.interpreter.Val;
import io.sapl.broker.impl.TimeOutWrapper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public class EmbeddedAttributeBroker implements AttributeBroker {

    static final String   UNKNOWN_ATTRIBUTE_ERROR                     = "Unknown attribute: '%s'.";
    static final Duration DEFAULT_TIMEOUT_FOR_INITIAL_ATTRIBUTE_VALUE = Duration.ofMillis(500L);
    static final Duration DEFAULT_TIME_TO_LIVE_FOR_CACHE_ENTRIES      = Duration.ofSeconds(10L);

    private final AtomicReference<Duration> defaultAttributeTimeOut = new AtomicReference<>(
            DEFAULT_TIMEOUT_FOR_INITIAL_ATTRIBUTE_VALUE);
    private final AtomicReference<Duration> defaultCacheTtl         = new AtomicReference<>(
            DEFAULT_TIME_TO_LIVE_FOR_CACHE_ENTRIES);

    private final Many<Val> oneAttributeValueSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<Val> oneAttributeValueFlux = oneAttributeValueSink.asFlux();

    private final Map<AttributeSubscriptionKey, AttributeSubscription> subscriptions = new ConcurrentHashMap<>();

    private final AttributeCache cache = new AttributeCache();

    public EmbeddedAttributeBroker() {
        var justNumbers = Flux.interval(Duration.ofSeconds(2L)).map(Val::of);
        justNumbers.doOnNext(oneAttributeValueSink::tryEmitNext).subscribe();
    }

    public void setDefaultAttributeTimeOut(Duration timeOut) {
        this.defaultAttributeTimeOut.set(timeOut);
    }

    public void setdefaultCacheTtl(Duration timeOut) {
        this.defaultCacheTtl.set(timeOut);
    }

    record AttributeSubscriptionKey(String attributeName, Val entity, List<Val> arguments, Map<String, Val> variables) {
        public AttributeSubscriptionKey(String attributeName, List<Val> arguments, Map<String, Val> variables) {
            this(attributeName, null, arguments, variables);
        }
    }

    record AttributeSubscription(Many<Val> sink, Disposable disposableSource) {
    }

    record CacheEntry(Val value, Instant timestamp, Duration ttl) {
        public boolean isExpired() {
            return Instant.now().isAfter(timestamp.plus(ttl));
        }
    }

    private Flux<Val> subscribe(AttributeSubscriptionKey key) {
        return oneAttributeValueFlux;
    }

    @Override
    public Flux<Val> evaluateEntityAttribute(String attributeName, Val entity, List<Val> arguments, boolean fresh,
            Duration initialTimeOut, Map<String, Val> variables) {
        var       key      = new AttributeSubscriptionKey(attributeName, entity, arguments, variables);
        Mono<Val> cacheHit = Mono.empty();
        if (!fresh) {
            cacheHit = cache.lookupCache(key);
        }
        return TimeOutWrapper.wrap(cacheHit.thenMany(subscribe(key)), initialTimeOut);
    }

    @Override
    public Flux<Val> evaluateEntityAttribute(String attributeName, Val entity, List<Val> arguments, boolean fresh,
            Map<String, Val> variables) {
        return evaluateEntityAttribute(attributeName, entity, arguments, fresh, this.defaultAttributeTimeOut.get(),
                variables);
    }

    @Override
    public Flux<Val> evaluateEnvironmentAttribute(String attributeName, List<Val> arguments, boolean fresh,
            Duration initialTimeOut, Map<String, Val> variables) {
        var       key      = new AttributeSubscriptionKey(attributeName, arguments, variables);
        Mono<Val> cacheHit = Mono.empty();
        if (!fresh) {
            cacheHit = cache.lookupCache(key);
        }
        return TimeOutWrapper.wrap(cacheHit.thenMany(subscribe(key)), initialTimeOut);
    }

    @Override
    public Flux<Val> evaluateEnvironmentAttribute(String attributeName, List<Val> arguments, boolean fresh,
            Map<String, Val> variables) {
        return evaluateEnvironmentAttribute(attributeName, arguments, fresh, this.defaultAttributeTimeOut.get(),
                variables);
    }

    public void publishEntityAttributeValue(String attributeName, Val entity, Val attributeValue) {
        // TODO
    }

    public void publishEnvironmentAttributeValue(String attributeName, Val attributeValue) {
        // TODO
    }

}
