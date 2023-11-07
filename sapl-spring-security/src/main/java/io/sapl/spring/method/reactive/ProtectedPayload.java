package io.sapl.spring.method.reactive;

import java.util.NoSuchElementException;

import javax.annotation.Nonnull;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

/**
 * A wrapper class to enable onErrorContinue with in protected Flux processing.
 *
 * @param <P> Payload type
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class ProtectedPayload<P> {
    private final P         payload;
    private final Throwable error;

    /**
     * Creates a ProtectedPayload
     *
     * @param <T>     the payload type.
     * @param payload a payload
     * @return a ProtectedPayload containing the payload value
     */
    public static <T> ProtectedPayload<T> withPayload(@NonNull T payload) {
        return new ProtectedPayload<>(payload, null);
    }

    /**
     * Creates a ProtectedPayload
     *
     * @param <T>       the payload type.
     * @param exception an Exception
     * @return a ProtectedPayload containing the exception
     */
    public static <T> ProtectedPayload<T> withError(@NonNull Throwable exception) {
        return new ProtectedPayload<>(null, exception);
    }

    /**
     * Get the payload or throw Exception.
     *
     * Explanation: Why is this a Mono<>? Answer: Because onErrorContinue does no
     * longer work with map but only with flatMap
     *
     * @return a Mono of th payload
     */
    @SneakyThrows
    public Mono<P> getPayload() {
        if (error != null)
            throw error;
        return Mono.just(payload);
    }

    /**
     * @return true, if this wraps an Exception and not a payload.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * @return true, if this wraps a payload.
     */
    public boolean hasPayload() {
        return payload != null;
    }

    /**
     * @return the wrapped exception, or NoSuchElementException
     */
    @Nonnull
    public Throwable getError() {
        if (error == null)
            throw new NoSuchElementException("Protected payload does not wrap an exception.");
        return error;
    }

}
