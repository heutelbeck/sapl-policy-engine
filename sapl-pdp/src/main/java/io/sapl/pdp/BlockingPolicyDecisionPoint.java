/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.pdp;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.ast.Outcome;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.BrokerEvalLoops;
import io.sapl.attributes.broker.EvaluationException;
import io.sapl.attributes.broker.HeadCache;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.document.Voters;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reactor-free policy decision point implementing
 * {@link StreamingPolicyDecisionPoint}. Drives the compiled PDP voter
 * and the engine-internal coverage voter against a per-evaluation
 * {@link AttributeBroker} subscription, exposing decisions through the
 * SAPL {@link Stream} primitive. Consumers block on each
 * {@link Stream#awaitNext()} call.
 * <p>
 * Registered {@link DecisionInterceptor}s observe each decision the PDP
 * publishes. Registered {@link SubscriptionLifecycleListener}s receive
 * one {@code onSubscribe} call when each subscription stream begins and
 * one {@code onUnsubscribe} call when it ends.
 */
@Slf4j
public final class BlockingPolicyDecisionPoint implements StreamingPolicyDecisionPoint {

    private static final String ERROR_EVALUATOR_THREW            = "Voter evaluation failed.";
    private static final String ERROR_INTERRUPTED                = "Voter evaluation interrupted.";
    private static final String ERROR_NO_PDP_CONFIGURATION       = "No PDP configuration found.";
    private static final String ERROR_UNEXPECTED_EVALUATION      = "Unexpected error during decision evaluation, returning INDETERMINATE.";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION = "Voter produced no decision.";

    private static final String WARN_LISTENER_THREW = "Observability listener {} threw and was isolated from authorization. Further failures from this class are suppressed.";

    private static final Set<String> warnedListenerClasses = ConcurrentHashMap.newKeySet();

    private final PdpVoterSource  pdpConfigurationSource;
    private final AttributeBroker attributeBroker;
    private final IdFactory       idFactory;
    private final InstantSource   timestampSource;

    public BlockingPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeBroker attributeBroker,
            IdFactory idFactory) {
        this(pdpConfigurationSource, attributeBroker, idFactory, Clock.systemUTC());
    }

    public BlockingPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeBroker attributeBroker,
            IdFactory idFactory,
            InstantSource timestampSource) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.attributeBroker        = attributeBroker;
        this.idFactory              = idFactory;
        this.timestampSource        = timestampSource;
    }

    private List<DecisionInterceptor> decisionInterceptors() {
        return pdpConfigurationSource.getPlugins().decisionInterceptors();
    }

    private List<SubscriptionLifecycleListener> lifecycleListeners() {
        return pdpConfigurationSource.getPlugins().lifecycleListeners();
    }

    private boolean hasDecisionInterceptors() {
        return !decisionInterceptors().isEmpty();
    }

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        notifyOnSubscribe(lifecycleListeners(), subscriptionId, sub, pdpId);
        try {
            if (hasDecisionInterceptors()) {
                val tv = computeTracedVoteSync(sub, subscriptionId, pdpId);
                dispatchDecisionObservers(decisionInterceptors(), tv, tv.timestamp(), subscriptionId, sub);
                return tv.authorizationDecision();
            }
            return computeVoteSync(sub, subscriptionId, pdpId).authorizationDecision();
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return AuthorizationDecision.INDETERMINATE;
        } finally {
            notifyOnUnsubscribe(lifecycleListeners(), subscriptionId);
        }
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        notifyOnSubscribe(lifecycleListeners(), subscriptionId, sub, pdpId);
        val source  = hasDecisionInterceptors()
                ? rewireOnConfigChange(pdpId, maybePdp -> evaluateDecisionsTraced(maybePdp, sub, subscriptionId, pdpId))
                : rewireOnConfigChange(pdpId, maybePdp -> evaluateDecisions(maybePdp, sub, subscriptionId, pdpId));
        val deduped = Streams.distinctUntilChanged(source, e -> AuthorizationDecision.INDETERMINATE);
        return withUnsubscribeNotification(deduped, subscriptionId);
    }

    private Stream<AuthorizationDecision> evaluateDecisions(Optional<CompiledPdp> maybePdp,
            AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        try {
            val voteSource = maybePdp.map(pdp -> voteStream(pdp, sub, subscriptionId))
                    .orElseGet(() -> singleton(noConfigurationVote(pdpId)));
            return mapStream(voteSource, Vote::authorizationDecision);
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return singleton(AuthorizationDecision.INDETERMINATE);
        }
    }

    private Stream<AuthorizationDecision> evaluateDecisionsTraced(Optional<CompiledPdp> maybePdp,
            AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        try {
            val tracedSource = maybePdp.map(pdp -> tracedVoteStream(pdp, sub, subscriptionId))
                    .orElseGet(() -> singleton(tracedNoConfiguration(pdpId, timestampSource)));
            return mapStream(tracedSource, tv -> {
                dispatchDecisionObservers(decisionInterceptors(), tv, tv.timestamp(), subscriptionId, sub);
                return tv.authorizationDecision();
            });
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return singleton(AuthorizationDecision.INDETERMINATE);
        }
    }

    private <T> Stream<T> rewireOnConfigChange(String pdpId, Function<Optional<CompiledPdp>, Stream<T>> innerFactory) {
        val out = new LatestSlotStream<T>();
        switchOnConfig(pdpId, out::put, out::onClose, innerFactory);
        return out;
    }

    private <T> Stream<T> rewireOnConfigChangeBuffered(String pdpId,
            Function<Optional<CompiledPdp>, Stream<T>> innerFactory) {
        val out = new QueueStream<T>();
        switchOnConfig(pdpId, out::put, out::onClose, innerFactory);
        return out;
    }

    @Override
    public Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return singleton(IdentifiableAuthorizationDecision.INDETERMINATE);
        }
        val previous = new AtomicReference<MultiAuthorizationDecision>();
        val source   = decideAll(multiSubscription, pdpId);
        return identifiableChangeStream(source, previous);
    }

    @Override
    public Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return singleton(MultiAuthorizationDecision.indeterminate());
        }
        val subscriptionId = idFactory.newRandom();
        val listenerIds    = notifyOnSubscribePerElement(multiSubscription, pdpId);
        val raw            = rewireOnConfigChange(pdpId,
                maybePdp -> evaluateMulti(maybePdp, multiSubscription, subscriptionId));
        val deduped        = Streams.distinctUntilChanged(raw, e -> MultiAuthorizationDecision.indeterminate());
        return withUnsubscribeNotification(deduped, listenerIds);
    }

    private List<String> notifyOnSubscribePerElement(MultiAuthorizationSubscription multiSubscription, String pdpId) {
        val listeners   = lifecycleListeners();
        val listenerIds = new ArrayList<String>();
        for (val identifiable : multiSubscription) {
            val listenerId = idFactory.newRandom();
            listenerIds.add(listenerId);
            notifyOnSubscribe(listeners, listenerId, identifiable.subscription(), pdpId);
        }
        return listenerIds;
    }

    private Stream<MultiAuthorizationDecision> evaluateMulti(Optional<CompiledPdp> maybePdp,
            MultiAuthorizationSubscription multiSubscription, String subscriptionId) {
        try {
            return maybePdp.map(pdp -> multiVoteStream(multiSubscription, pdp, subscriptionId))
                    .orElseGet(() -> singleton(MultiAuthorizationDecision.indeterminate()));
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return singleton(MultiAuthorizationDecision.indeterminate());
        }
    }

    /**
     * Streams the {@link TracedVote}s the PDP produces for the
     * subscription. Each vote carries the verb, emit timestamp,
     * dependency map, and per-key snapshot read. Re-evaluates on every PDP
     * configuration change
     * with the same semantics as {@link #decide}; the stream stays
     * alive until the consumer closes it.
     * <p>
     * Engine-internal: consumed by tooling (playground, tests) that
     * needs the full per-round trace, not just the final decision.
     */
    public Stream<TracedVote> gatherVotes(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        return rewireOnConfigChange(pdpId, maybePdp -> evaluateTracedVotes(maybePdp, sub, subscriptionId, pdpId));
    }

    private Stream<TracedVote> evaluateTracedVotes(Optional<CompiledPdp> maybePdp, AuthorizationSubscription sub,
            String subscriptionId, String pdpId) {
        try {
            return maybePdp.map(pdp -> tracedVoteStream(pdp, sub, subscriptionId))
                    .orElseGet(() -> singleton(tracedNoConfiguration(pdpId, timestampSource)));
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return singleton(tracedNoConfiguration(pdpId, timestampSource));
        }
    }

    /**
     * Engine-internal one-shot evaluation that records branch coverage
     * alongside the vote. Not part of the public
     * {@link StreamingPolicyDecisionPoint} contract; consumed by
     * sapl-test and tooling.
     */
    public VoteWithCoverage decideOnceWithCoverage(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId   = idFactory.newRandom();
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            return new VoteWithCoverage(noConfigurationVote(pdpId), null);
        }
        val pdp     = pdpConfiguration.get();
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        try {
            val initial = pdp.coverageVoter().evaluate(baseCtx);
            if (initial.voteResult().dependencies().isEmpty()) {
                val v = initial.voteResult().vote();
                return new VoteWithCoverage(v == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : v,
                        initial.coverage());
            }
            if (initial.voteResult().vote() != null) {
                return new VoteWithCoverage(initial.voteResult().vote(), initial.coverage());
            }
            return Voters.awaitFirstVoteWithCoverage(attributeBroker, subscriptionId,
                    initial.voteResult().dependencies().keySet(),
                    snapshot -> pdp.coverageVoter().evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new VoteWithCoverage(errorVote(pdp, ERROR_INTERRUPTED), null);
        } catch (EvaluationException ee) {
            return new VoteWithCoverage(errorVote(pdp, ERROR_EVALUATOR_THREW), null);
        } catch (RuntimeException re) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, re);
            return new VoteWithCoverage(errorVote(pdp, ERROR_EVALUATOR_THREW), null);
        }
    }

    /**
     * Engine-internal streaming evaluation that emits a fresh
     * {@link VoteWithCoverage} every round. Coverage emissions are NOT
     * deduplicated and are buffered through a {@link QueueStream}: a
     * slow consumer must observe every round's branch hits. Re-evaluates
     * on every PDP configuration change, just like the public decision
     * methods.
     */
    public Stream<VoteWithCoverage> decideWithCoverage(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        return rewireOnConfigChangeBuffered(pdpId, maybePdp -> evaluateCoverage(maybePdp, sub, subscriptionId, pdpId));
    }

    private Stream<VoteWithCoverage> evaluateCoverage(Optional<CompiledPdp> maybePdp, AuthorizationSubscription sub,
            String subscriptionId, String pdpId) {
        try {
            return maybePdp.map(pdp -> coverageStream(pdp, sub, subscriptionId))
                    .orElseGet(() -> singleton(new VoteWithCoverage(noConfigurationVote(pdpId), null)));
        } catch (RuntimeException e) {
            log.warn(ERROR_UNEXPECTED_EVALUATION, e);
            return singleton(new VoteWithCoverage(noConfigurationVote(pdpId), null));
        }
    }

    private Stream<VoteWithCoverage> coverageStream(CompiledPdp pdp, AuthorizationSubscription sub,
            String subscriptionId) {
        val out     = new QueueStream<VoteWithCoverage>();
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        val initial = pdp.coverageVoter().evaluate(baseCtx);
        if (initial.voteResult().dependencies().isEmpty()) {
            val v = initial.voteResult().vote();
            out.put(new VoteWithCoverage(v == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : v,
                    initial.coverage()));
            out.complete();
            return out;
        }
        if (initial.voteResult().vote() != null) {
            out.put(new VoteWithCoverage(initial.voteResult().vote(), initial.coverage()));
        }
        val handle = BrokerEvalLoops.openWithHead(attributeBroker, subscriptionId,
                initial.voteResult().dependencies().keySet(),
                snap -> pdp.coverageVoter().evaluate(baseCtx.withSnapshot(snap)), (r, snap) -> {
                    if (r.voteResult().vote() != null) {
                        out.put(new VoteWithCoverage(r.voteResult().vote(), r.coverage()));
                    }
                }, r -> r.voteResult().dependencies().keySet());
        out.onClose(handle::close);
        return out;
    }

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription sub) {
        return decideOnce(sub, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription sub) {
        return decide(sub, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return decide(multiSubscription, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return decideAll(multiSubscription, DEFAULT_PDP_ID);
    }

    public VoteWithCoverage decideOnceWithCoverage(AuthorizationSubscription sub) {
        return decideOnceWithCoverage(sub, DEFAULT_PDP_ID);
    }

    public Stream<VoteWithCoverage> decideWithCoverage(AuthorizationSubscription sub) {
        return decideWithCoverage(sub, DEFAULT_PDP_ID);
    }

    public Stream<TracedVote> gatherVotes(AuthorizationSubscription sub) {
        return gatherVotes(sub, DEFAULT_PDP_ID);
    }

    private Vote computeVoteSync(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            return noConfigurationVote(pdpId);
        }
        return evaluateOnceSync(pdpConfiguration.get(), sub, subscriptionId);
    }

    private Vote evaluateOnceSync(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> v;
        case PureVoter p   -> p.vote(baseCtx);
        case StreamVoter s -> evaluateStreamingSync(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Vote evaluateStreamingSync(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote();
        }
        if (initial.vote() != null) {
            return initial.vote();
        }
        try {
            return Voters.awaitFirstVote(attributeBroker, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return errorVote(pdp, ERROR_INTERRUPTED);
        } catch (EvaluationException ee) {
            return errorVote(pdp, ERROR_EVALUATOR_THREW);
        }
    }

    private TracedVote computeTracedVoteSync(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        return pdpConfiguration.map(pdp -> evaluateOnceTracedSync(pdp, sub, subscriptionId))
                .orElseGet(() -> tracedNoConfiguration(pdpId, timestampSource));
    }

    private TracedVote evaluateOnceTracedSync(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> TracedVote.of(v, timestampSource.instant());
        case PureVoter p   -> TracedVote.of(p.vote(baseCtx), timestampSource.instant());
        case StreamVoter s -> evaluateStreamingTracedSync(pdp, baseCtx, subscriptionId, s);
        };
    }

    private TracedVote evaluateStreamingTracedSync(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return TracedVote.of(
                    initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote(),
                    timestampSource.instant());
        }
        if (initial.vote() != null) {
            return TracedVote.of(initial.vote(), timestampSource.instant());
        }
        try {
            return Voters.awaitFirstTracedVote(attributeBroker, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)), timestampSource);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return TracedVote.of(errorVote(pdp, ERROR_INTERRUPTED), timestampSource.instant());
        } catch (EvaluationException ee) {
            return TracedVote.of(errorVote(pdp, ERROR_EVALUATOR_THREW), timestampSource.instant());
        }
    }

    /**
     * Spawns the configuration-change pump that drives {@code outputSink}
     * with a fresh inner evaluator on every configuration change for
     * {@code pdpId}. Configuration removals route to {@code
     * Optional.empty()} so {@code innerFactory} can produce a "no
     * configuration" stream. Inner streams that complete (e.g., static
     * voters) leave the output silent until the next config event.
     * <p>
     * The output stream type is the caller's choice (latest-slot for
     * dedup-friendly decision flows, queued for coverage where every
     * emission must reach the consumer); the caller passes the put and
     * onClose method references and binds them to whichever stream it
     * returns to its consumer.
     */
    private <T> void switchOnConfig(String pdpId, Consumer<T> outputSink, Consumer<Runnable> closeBinder,
            Function<Optional<CompiledPdp>, Stream<T>> innerFactory) {
        val configEvents   = configurationEventStream(pdpId);
        val innerStreamRef = new AtomicReference<Stream<T>>();
        val innerPumpRef   = new AtomicReference<Thread>();
        val configPump     = Thread.startVirtualThread(
                () -> driveConfigChanges(configEvents, innerStreamRef, innerPumpRef, outputSink, innerFactory));
        closeBinder.accept(() -> {
            configPump.interrupt();
            configEvents.close();
        });
    }

    private <T> void driveConfigChanges(Stream<Optional<CompiledPdp>> configEvents,
            AtomicReference<Stream<T>> innerStreamRef, AtomicReference<Thread> innerPumpRef, Consumer<T> outputSink,
            Function<Optional<CompiledPdp>, Stream<T>> innerFactory) {
        // Each config swap bumps the generation. A pump may only latch a value while
        // its
        // generation is current, so a stale decision from a superseded configuration
        // can
        // never overwrite the fresh one (the check and latch are atomic under
        // sinkLock).
        val sinkLock   = new ReentrantLock();
        val generation = new AtomicLong();
        try {
            while (!Thread.interrupted()) {
                val configState = configEvents.awaitNext();
                if (configState == null) {
                    return;
                }
                terminateCurrentInner(innerPumpRef, innerStreamRef);
                val newInner = innerFactory.apply(configState);
                val myGen    = generation.incrementAndGet();
                innerStreamRef.set(newInner);
                innerPumpRef.set(Thread.startVirtualThread(() -> pumpInto(newInner,
                        value -> latchIfCurrent(sinkLock, generation, myGen, outputSink, value))));
            }
        } catch (InterruptedException expected) {
            Thread.currentThread().interrupt();
        } finally {
            terminateCurrentInner(innerPumpRef, innerStreamRef);
        }
    }

    private static <T> void latchIfCurrent(ReentrantLock sinkLock, AtomicLong generation, long myGen, Consumer<T> sink,
            T value) {
        sinkLock.lock();
        try {
            if (generation.get() == myGen) {
                sink.accept(value);
            }
        } finally {
            sinkLock.unlock();
        }
    }

    private static <T> void pumpInto(Stream<T> source, Consumer<T> sink) {
        // try-with-resources closes the inner source on every exit, including its own
        // completion. terminateCurrentInner is only a backstop on config swap/teardown.
        try (source) {
            while (!Thread.interrupted()) {
                val value = source.awaitNext();
                if (value == null) {
                    return;
                }
                sink.accept(value);
            }
        } catch (InterruptedException expected) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> void terminateCurrentInner(AtomicReference<Thread> pumpRef,
            AtomicReference<Stream<T>> streamRef) {
        val previousPump = pumpRef.getAndSet(null);
        if (previousPump != null) {
            previousPump.interrupt();
        }
        val previousInner = streamRef.getAndSet(null);
        if (previousInner != null) {
            previousInner.close();
        }
    }

    /**
     * Hot stream of compiled-PDP states for {@code pdpId}: emits the
     * current snapshot immediately, then one item on every Load or Remove
     * event. Stays alive until the consumer closes it.
     */
    private Stream<Optional<CompiledPdp>> configurationEventStream(String pdpId) {
        val                      out      = new LatestSlotStream<Optional<CompiledPdp>>();
        Consumer<PdpUpdateEvent> listener = event -> {
                                              switch (event) {
                                              case PdpUpdateEvent.Voter(var ignoredId, var voter) ->
                                                  out.put(Optional.of(voter));
                                              case PdpUpdateEvent.Removed(var ignoredId)          ->
                                                  out.put(Optional.empty());
                                              }
                                          };
        // subscribeToUpdates delivers the current configuration to the listener
        // under the source lock, so the initial value and any concurrent update
        // arrive in order and a stale configuration cannot latch here.
        pdpConfigurationSource.subscribeToUpdates(pdpId, listener);
        out.onClose(() -> pdpConfigurationSource.unsubscribeFromUpdates(pdpId, listener));
        return out;
    }

    private Stream<Vote> voteStream(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> singleton(v);
        case PureVoter p   -> singleton(p.vote(baseCtx));
        case StreamVoter s -> streamingVoteStream(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Stream<Vote> streamingVoteStream(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val out     = new LatestSlotStream<Vote>();
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            out.put(initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote());
            out.complete();
            return out;
        }
        if (initial.vote() != null) {
            out.put(initial.vote());
        }
        val handle = BrokerEvalLoops.openWithHead(attributeBroker, subscriptionId, initial.dependencies().keySet(),
                snap -> voter.evaluate(baseCtx.withSnapshot(snap)), (r, snap) -> {
                    if (r.vote() != null) {
                        out.put(r.vote());
                    }
                }, r -> r.dependencies().keySet());
        out.onClose(handle::close);
        return out;
    }

    private Stream<TracedVote> tracedVoteStream(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> singleton(TracedVote.of(v, timestampSource.instant()));
        case PureVoter p   -> singleton(TracedVote.of(p.vote(baseCtx), timestampSource.instant()));
        case StreamVoter s -> streamingTracedVoteStream(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Stream<TracedVote> streamingTracedVoteStream(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        val out     = new LatestSlotStream<TracedVote>();
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            val v = initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote();
            out.put(TracedVote.of(v, timestampSource.instant()));
            out.complete();
            return out;
        }
        if (initial.vote() != null) {
            out.put(TracedVote.of(initial.vote(), timestampSource.instant()));
        }
        val handle = BrokerEvalLoops.openWithHead(attributeBroker, subscriptionId, initial.dependencies().keySet(),
                snap -> voter.evaluate(baseCtx.withSnapshot(snap)), (r, snap) -> {
                    if (r.vote() != null) {
                        out.put(buildTracedVote(r, snap, timestampSource));
                    }
                }, r -> r.dependencies().keySet());
        out.onClose(handle::close);
        return out;
    }

    /**
     * Builds a {@link TracedVote} from a freshly-computed
     * {@link VoteResult} plus the snapshot the round read. Shared with
     * the reactive PDP so a single algorithm produces traced votes
     * regardless of which transport the consumer uses.
     */
    public static TracedVote buildTracedVote(VoteResult result, Map<SubscriptionKey, AttributeSnapshot> snapshot,
            InstantSource timestampSource) {
        return new TracedVote(result.vote(), timestampSource.instant(), result.dependencies(),
                Voters.readSnapshot(result, snapshot));
    }

    private Stream<MultiAuthorizationDecision> multiVoteStream(MultiAuthorizationSubscription multiSubscription,
            CompiledPdp pdp, String subscriptionId) {
        val items = new ArrayList<IdentifiableAuthorizationSubscription>();
        for (val item : multiSubscription) {
            items.add(item);
        }
        val out         = new LatestSlotStream<MultiAuthorizationDecision>();
        val initialDeps = new HashSet<SubscriptionKey>();
        val initial     = evaluateRound(items, pdp, subscriptionId, Map.of(), initialDeps);
        if (initial != null) {
            out.put(initial);
        }
        if (initialDeps.isEmpty()) {
            out.complete();
            return out;
        }
        val headCache = new HeadCache();
        val handle    = attributeBroker.open(subscriptionId, headCache.brokerDepsFor(initialDeps), brokerSnap -> {
                          val newDeps = new HashSet<SubscriptionKey>();
                          val multi   = evaluateRound(items, pdp, subscriptionId, headCache.merge(brokerSnap), newDeps);
                          if (multi != null) {
                              out.put(multi);
                          }
                          val effective = newDeps.isEmpty() ? initialDeps : newDeps;
                          headCache.captureFrom(brokerSnap);
                          headCache.retainOnly(effective);
                          return headCache.brokerDepsFor(effective);
                      });
        out.onClose(handle::close);
        return out;
    }

    /**
     * Evaluates one snapshot round across every sub in a multi-
     * subscription bundle. Returns {@code null} when any sub fails to
     * produce a vote (the round is suppressed); otherwise returns the
     * combined {@link MultiAuthorizationDecision}, accumulates every
     * dependency the round read into {@code depsAccumulator}, and (if
     * supplied) hands each per-sub traced vote to {@code
     * perSubTraceObserver}. Shared between both PDPs so the multi-vote
     * loop is one implementation.
     */
    public static MultiAuthorizationDecision evaluateRound(List<IdentifiableAuthorizationSubscription> items,
            CompiledPdp pdp, String subscriptionId, Map<SubscriptionKey, AttributeSnapshot> snapshot,
            Set<SubscriptionKey> depsAccumulator, FunctionBroker functionBroker, InstantSource timestampSource,
            BiConsumer<TracedVote, IdentifiableAuthorizationSubscription> perSubTraceObserver) {
        val multi = new MultiAuthorizationDecision();
        for (val item : items) {
            val ctx = evaluationContext(pdp, item.subscription(), subscriptionId, functionBroker)
                    .withSnapshot(snapshot);
            val r   = evaluateVoter(pdp.voter(), ctx);
            depsAccumulator.addAll(r.dependencies().keySet());
            if (r.vote() == null) {
                return null;
            }
            if (perSubTraceObserver != null) {
                perSubTraceObserver.accept(buildTracedVote(r, snapshot, timestampSource), item);
            }
            multi.setDecision(item.subscriptionId(), r.vote().authorizationDecision());
        }
        return multi;
    }

    /**
     * Dispatches a {@link Voter} variant to its evaluator. Shared
     * between both PDPs so the variant table lives in one place.
     */
    public static VoteResult evaluateVoter(Voter voter, EvaluationContext ctx) {
        return switch (voter) {
        case Vote v        -> new VoteResult(v, Map.of());
        case PureVoter p   -> new VoteResult(p.vote(ctx), Map.of());
        case StreamVoter s -> s.evaluate(ctx);
        };
    }

    private Stream<IdentifiableAuthorizationDecision> identifiableChangeStream(
            Stream<MultiAuthorizationDecision> source, AtomicReference<MultiAuthorizationDecision> previousRef) {
        val out  = new QueueStream<IdentifiableAuthorizationDecision>();
        val pump = Thread.startVirtualThread(() -> pumpChangedDecisions(source, previousRef, out));
        out.onClose(() -> {
            pump.interrupt();
            source.close();
        });
        return out;
    }

    private static void pumpChangedDecisions(Stream<MultiAuthorizationDecision> source,
            AtomicReference<MultiAuthorizationDecision> previousRef,
            QueueStream<IdentifiableAuthorizationDecision> out) {
        try {
            while (true) {
                val current = source.awaitNext();
                if (current == null) {
                    return;
                }
                emitDecisionDiffs(current, previousRef.getAndSet(current), out);
            }
        } catch (InterruptedException expected) {
            Thread.currentThread().interrupt();
        } finally {
            out.complete();
            source.close();
        }
    }

    private static void emitDecisionDiffs(MultiAuthorizationDecision current, MultiAuthorizationDecision previous,
            QueueStream<IdentifiableAuthorizationDecision> out) {
        for (val identifiable : current) {
            val prevDecision = previous == null ? null : previous.getDecision(identifiable.subscriptionId());
            if (!Objects.equals(prevDecision, identifiable.decision())) {
                out.put(identifiable);
            }
        }
    }

    private static <T> Stream<T> singleton(T value) {
        val out = new LatestSlotStream<T>();
        out.put(value);
        out.complete();
        return out;
    }

    private static <S, T> Stream<T> mapStream(Stream<S> source, Function<S, T> mapper) {
        val out  = new LatestSlotStream<T>();
        val pump = Thread.startVirtualThread(() -> {
                     try {
                         while (true) {
                             val v = source.awaitNext();
                             if (v == null) {
                                 return;
                             }
                             out.put(mapper.apply(v));
                         }
                     } catch (InterruptedException expected) {
                         Thread.currentThread().interrupt();
                     } finally {
                         out.complete();
                         source.close();
                     }
                 });
        out.onClose(() -> {
            pump.interrupt();
            source.close();
        });
        return out;
    }

    private <T> Stream<T> withUnsubscribeNotification(Stream<T> source, String subscriptionId) {
        return withUnsubscribeNotification(source, List.of(subscriptionId));
    }

    private <T> Stream<T> withUnsubscribeNotification(Stream<T> source, List<String> subscriptionIds) {
        val out  = new LatestSlotStream<T>();
        val pump = Thread.startVirtualThread(() -> {
                     try {
                         while (true) {
                             val v = source.awaitNext();
                             if (v == null) {
                                 return;
                             }
                             out.put(v);
                         }
                     } catch (InterruptedException expected) {
                         Thread.currentThread().interrupt();
                     } finally {
                         out.complete();
                         source.close();
                         val listeners = lifecycleListeners();
                         for (val subscriptionId : subscriptionIds) {
                             notifyOnUnsubscribe(listeners, subscriptionId);
                         }
                     }
                 });
        out.onClose(() -> {
            pump.interrupt();
            source.close();
        });
        return out;
    }

    /**
     * Builds the per-evaluation {@link EvaluationContext}. Shared with
     * the reactive PDP so context wiring (pdpId, configurationId,
     * subscriptionId, function broker) is one assembly.
     */
    public static EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub,
            String subscriptionId, FunctionBroker functionBroker) {
        return EvaluationContext.of(pdp.metadata().pdpId(), pdp.metadata().configurationId(), subscriptionId, sub,
                functionBroker);
    }

    private EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        return evaluationContext(pdp, sub, subscriptionId, pdp.plugins().functionBroker());
    }

    private MultiAuthorizationDecision evaluateRound(List<IdentifiableAuthorizationSubscription> items, CompiledPdp pdp,
            String subscriptionId, Map<SubscriptionKey, AttributeSnapshot> snapshot,
            Set<SubscriptionKey> depsAccumulator) {
        return evaluateRound(items, pdp, subscriptionId, snapshot, depsAccumulator, pdp.plugins().functionBroker(),
                timestampSource, hasDecisionInterceptors() ? this::observePerSubTrace : null);
    }

    private void observePerSubTrace(TracedVote perSubTraced, IdentifiableAuthorizationSubscription item) {
        dispatchDecisionObservers(decisionInterceptors(), perSubTraced, perSubTraced.timestamp(), item.subscriptionId(),
                item.subscription());
    }

    /**
     * Synthesises the "no PDP configuration" vote returned to consumers
     * subscribing while the bound pdpId has no compiled configuration.
     */
    public static Vote noConfigurationVote(String pdpId) {
        val metadata = new PdpVoterMetadata("no-configuration", pdpId, "none", null, Outcome.PERMIT_OR_DENY, false);
        return Vote.error(new ErrorValue(ERROR_NO_PDP_CONFIGURATION), metadata);
    }

    /**
     * Wraps {@link #noConfigurationVote(String)} as a {@link TracedVote}
     * with the current emit timestamp.
     */
    public static TracedVote tracedNoConfiguration(String pdpId, InstantSource timestampSource) {
        return TracedVote.of(noConfigurationVote(pdpId), timestampSource.instant());
    }

    /**
     * Builds an INDETERMINATE vote attributed to the supplied
     * {@code pdp}'s metadata, carrying {@code message} as the error.
     */
    public static Vote errorVote(CompiledPdp pdp, String message) {
        return Vote.error(new ErrorValue(message), pdp.metadata());
    }

    /**
     * Fires {@code onSubscribe} on every registered listener. A
     * misbehaving observer cannot affect authorization correctness, so
     * exceptions are isolated and logged once per listener class. Fatal
     * {@link VirtualMachineError}s are never masked. Shared with the
     * reactive PDP.
     */
    public static void notifyOnSubscribe(List<SubscriptionLifecycleListener> listeners, String subscriptionId,
            AuthorizationSubscription sub, String pdpId) {
        for (val listener : listeners) {
            try {
                listener.onSubscribe(subscriptionId, sub, pdpId);
            } catch (Throwable t) {
                isolateObserverFailure(t, listener);
            }
        }
    }

    /**
     * Fires {@code onUnsubscribe} on every registered listener.
     * Exceptions are swallowed; see {@link #notifyOnSubscribe(List,
     * String, AuthorizationSubscription, String)}.
     */
    public static void notifyOnUnsubscribe(List<SubscriptionLifecycleListener> listeners, String subscriptionId) {
        for (val listener : listeners) {
            try {
                listener.onUnsubscribe(subscriptionId);
            } catch (Throwable t) {
                isolateObserverFailure(t, listener);
            }
        }
    }

    /**
     * Fires {@code onDecision} on every registered interceptor.
     * Exceptions are swallowed; see {@link #notifyOnSubscribe(List,
     * String, AuthorizationSubscription, String)}.
     */
    public static void dispatchDecisionObservers(List<DecisionInterceptor> interceptors, TracedDecision decision,
            Instant timestamp, String subscriptionId, AuthorizationSubscription sub) {
        for (val interceptor : interceptors) {
            try {
                interceptor.onDecision(decision, timestamp, subscriptionId, sub);
            } catch (Throwable t) {
                isolateObserverFailure(t, interceptor);
            }
        }
    }

    private static void isolateObserverFailure(Throwable t, Object observer) {
        if (t instanceof VirtualMachineError vme) {
            throw vme;
        }
        if (warnedListenerClasses.add(observer.getClass().getName())) {
            log.warn(WARN_LISTENER_THREW, observer.getClass().getName(), t);
        }
    }
}
