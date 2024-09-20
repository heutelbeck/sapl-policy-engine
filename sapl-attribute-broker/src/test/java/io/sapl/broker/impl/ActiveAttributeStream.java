package io.sapl.broker.impl;

import java.time.Duration;
import java.util.function.Consumer;

import io.sapl.api.interpreter.Val;
import lombok.Getter;
import reactor.core.publisher.Flux;

/**
 * An ActiveAttribute instance represents an attribute stream in use. The
 * attribute stream is identified by this fully qualified attribute name and its
 * parameter values, i.e., the so-called PolicyInformationPointinvocation.
 * 
 * This class is only to be used internally by the attribute broker for indexing
 * purposes.
 * 
 * The ActiveAttribute takes a the PolicyInformationPointinvocation, a Flux<Val>
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
public class ActiveAttributeStream {

    @Getter
    private final PolicyInformationPointInvocation invocation;
    @Getter
    private final Flux<Val>                        attributeStream;

    /**
     * Creates an ActiveAttribute.
     * 
     * @param invocation         A PolicyInformationPointinvocation providing the
     *                           fully qualified attribute name and its parameter
     *                           values.
     * @param pipAttributeStream The raw attribute stream for the given key supplied
     *                           by a policy information point connection.
     * @param cleanupAction      A callback to the index to be executed to drop the
     *                           attribute from the index upon termination.
     * @param gracePeriod        The time to live for the connection to the raw
     *                           stream, once all subscribers cancelled their
     *                           subscription.
     */
    public ActiveAttributeStream(PolicyInformationPointInvocation invocation, Flux<Val> pipAttributeStream,
            Consumer<ActiveAttributeStream> cleanupAction, Duration gracePeriod) {
        this.invocation = invocation;
        // @formatter:off
        this.attributeStream = pipAttributeStream.doOnCancel(() -> cleanupAction.accept(this))
                                                 .doAfterTerminate(() -> cleanupAction.accept(this))
                                                 .replay(1).refCount(1)
                                                 .publish().refCount(1, gracePeriod);
        // @formatter:on
    }
}