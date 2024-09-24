package io.sapl.broker.impl;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.sapl.api.interpreter.Val;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.retry.Retry;

/**
 * An instance represents an attribute stream in use. The attribute stream is
 * identified by this fully qualified attribute name and its parameter values,
 * i.e., the so-called PolicyInformationPointInvocation.
 * 
 * This class is only to be used internally by the attribute broker for indexing
 * purposes.
 * 
 * The ActiveAttribute takes a the PolicyInformationPointInvocation, a Flux<Val>
 * supplying the raw attribute stream matching the invocation, and a cleanup
 * action.
 * 
 * The raw attribute stream is turned into a cached multi-cast stream, which
 * will call the cleanup action when the last subscriber cancelled, or the
 * stream terminated for some reason.
 * 
 * The subscription to the raw attribute stream is kept alive for a given grace
 * period before it is cancelled, implementing a basic connection cache for
 * attribute streams.
 */
@Slf4j
public class AttributeStream {
    @Getter
    private final PolicyInformationPointInvocation invocation;

    private final Many<Val> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Getter
    private final Flux<Val> stream;

    private Consumer<AttributeStream>   cleanupAction;
    private AtomicReference<Disposable> pipSubscription = new AtomicReference<>();

    /**
     * Creates an ActiveAttribute.
     * 
     * @param invocation    A PolicyInformationPointinvocation providing the fully
     *                      qualified attribute name and its parameter values.
     * @param cleanupAction A callback to the index to be executed to drop the
     *                      attribute from the index upon termination.
     * @param gracePeriod   The time to live for the connection to the raw stream,
     *                      once all subscribers cancelled their subscription.
     */
    public AttributeStream(@NonNull PolicyInformationPointInvocation invocation,
            @NonNull Consumer<AttributeStream> cleanupAction, @NonNull Duration gracePeriod) {
        this.invocation    = invocation;
        this.cleanupAction = cleanupAction;
        // @formatter:off
        this.stream = sink.asFlux().doOnCancel(this::noMoreSubscribers)
                                   .doAfterTerminate(this::noMoreSubscribers)
                                   .replay(1)
                                   .refCount(1, gracePeriod);
        // @formatter:on
    }

    private void noMoreSubscribers() {
        log.info("No more subscribers for {}", this);
        cleanupAction.accept(this);
        disposeOfPip();
    }

    private void disposeOfPip() {
        log.info("Disposing PIP for {}", this);
        this.pipSubscription.getAndUpdate(s -> {
            if (null != s) {
                s.dispose();
            }
            return null;
        });
    }

    public void publish(Val v) {
        log.info("Publish {} to {}.", v, this);
        final var emitResult = sink.tryEmitNext(v);
        if (emitResult.isFailure()) {
            log.warn("Failed [{}] to emit value {} to {}", emitResult, v, this);
        }
    }

    public void publish(Throwable t) {
        publish(Val.error(t.getMessage()));
    }

    public void connectTo(PolicyInformationPoint policyInformationPoint) {
        log.info("Connecting {} to {}.", policyInformationPoint, this);
        // @formatter:off
        final var pipStream = addInitialTimeOut(
                              retryOnError(
                              pollOnComplete(policyInformationPoint.invoce(invocation)
                                                                   .defaultIfEmpty(Val.UNDEFINED)
                                                                   .doOnNext(this::publish)
                                                                   .doOnError(this::publish)
                                            )));
        // @formatter:on        
        pipSubscription.getAndUpdate(s -> {
            if (null != s) {
                log.info("While connecting {} to {}, there was already a subscription to a PIP. Dispose of it!",
                        policyInformationPoint, this);
                s.dispose();
            }
            return pipStream.subscribe();
        });
    }

    private Flux<Val> addInitialTimeOut(Flux<Val> attributeStream) {
        return TimeOutWrapper.wrap(attributeStream, invocation.initialTimeOut());
    }

    private Flux<Val> pollOnComplete(Flux<Val> attributeStream) {
        return attributeStream.thenMany(attributeStream.repeat().delayElements(invocation.pollIntervall()));
    }

    private Flux<Val> retryOnError(Flux<Val> attributeStream) {
        return attributeStream.retryWhen(Retry.backoff(invocation.retries(), invocation.backoff()));
    }

}
