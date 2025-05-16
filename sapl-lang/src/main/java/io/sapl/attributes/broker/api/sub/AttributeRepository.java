package io.sapl.attributes.broker.api.sub;

import java.time.Duration;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AttributeRepository {
    public enum TimeOutStrategy {
        REMOVE, BECOME_UNDEFINED
    }

    Mono<Void> publishAttribute(String fullyQualifiedName, Val value, Duration ttl, TimeOutStrategy timeOutStrategy);

    Mono<Void> publishAttribute(String fullyQualifiedName, Val value, Duration ttl);

    Mono<Void> publishAttribute(String fullyQualifiedName, Val value);

    Mono<Void> removeAttribute(String fullyQualifiedName);

    Flux<Val> subscribeToAttribute();

}
