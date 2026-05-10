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

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.ast.Outcome;
import io.sapl.attributes.store.AttributeStore;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.document.Voters;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Reactor-free policy decision point implementing
 * {@link StreamingPolicyDecisionPoint}. Drives the compiled PDP voter
 * and the engine-internal coverage voter against a per-evaluation
 * {@link AttributeStore} subscription, exposing decisions through the
 * SAPL {@link Stream} primitive. Consumers block on each
 * {@link Stream#awaitNext()} call.
 * <p>
 * Registered {@link VoteInterceptor}s receive the same lifecycle
 * callbacks ({@code onSubscribe}, {@code intercept}, {@code onUnsubscribe})
 * as in the reactive PDP.
 */
public final class BlockingPolicyDecisionPoint implements StreamingPolicyDecisionPoint {

    private static final String ERROR_INTERRUPTED                = "Voter evaluation interrupted.";
    private static final String ERROR_NO_PDP_CONFIGURATION       = "No PDP configuration found.";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION = "Voter produced no decision.";

    private final PdpVoterSource        pdpConfigurationSource;
    private final AttributeStore        attributeStore;
    private final IdFactory             idFactory;
    private final Clock                 clock;
    private final List<VoteInterceptor> interceptors;
    private final boolean               hasInterceptors;

    public BlockingPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), List.of());
    }

    public BlockingPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            List<VoteInterceptor> interceptors) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), interceptors);
    }

    public BlockingPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            Clock clock,
            List<VoteInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.attributeStore         = attributeStore;
        this.idFactory              = idFactory;
        this.clock                  = clock;
        this.interceptors           = interceptors.stream().sorted().toList();
        this.hasInterceptors        = !this.interceptors.isEmpty();
    }

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription sub, String pdpId) {
        return hasInterceptors ? tracedVoteSync(sub, pdpId).vote().authorizationDecision()
                : voteSync(sub, pdpId).authorizationDecision();
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription sub, String pdpId) {
        val source = hasInterceptors ? mapStream(tracedVoteStream(sub, pdpId), tv -> tv.vote().authorizationDecision())
                : mapStream(voteStream(sub, pdpId), Vote::authorizationDecision);
        return Streams.distinctUntilChanged(source, e -> AuthorizationDecision.INDETERMINATE);
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
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            return singleton(MultiAuthorizationDecision.indeterminate());
        }
        val subscriptionId = idFactory.newRandom();
        val raw            = multiVoteStream(multiSubscription, pdpConfiguration.get(), subscriptionId);
        return Streams.distinctUntilChanged(raw, e -> MultiAuthorizationDecision.indeterminate());
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
            return Voters.awaitFirstVoteWithCoverage(attributeStore, subscriptionId,
                    initial.voteResult().dependencies().keySet(),
                    snapshot -> pdp.coverageVoter().evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new VoteWithCoverage(errorVote(pdp, ERROR_INTERRUPTED), null);
        }
    }

    /**
     * Engine-internal streaming evaluation that emits a fresh
     * {@link VoteWithCoverage} every round. Coverage emissions are NOT
     * deduplicated and are buffered through a {@link QueueStream}: a
     * slow consumer must observe every round's branch hits.
     */
    public Stream<VoteWithCoverage> decideWithCoverage(AuthorizationSubscription sub, String pdpId) {
        val out              = new QueueStream<VoteWithCoverage>();
        val subscriptionId   = idFactory.newRandom();
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            out.put(new VoteWithCoverage(noConfigurationVote(pdpId), null));
            out.complete();
            return out;
        }
        val pdp     = pdpConfiguration.get();
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
        val handle = attributeStore.open(subscriptionId, initial.voteResult().dependencies().keySet(), snapshot -> {
            val r = pdp.coverageVoter().evaluate(baseCtx.withSnapshot(snapshot));
            if (r.voteResult().vote() != null) {
                out.put(new VoteWithCoverage(r.voteResult().vote(), r.coverage()));
            }
            return r.voteResult().dependencies().keySet();
        });
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

    private Vote voteSync(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId   = idFactory.newRandom();
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
            return Voters.awaitFirstVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return errorVote(pdp, ERROR_INTERRUPTED);
        }
    }

    private TracedVote tracedVoteSync(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        invokeOnSubscribe(subscriptionId, sub, pdpId);
        try {
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            val tracedVote       = pdpConfiguration.map(pdp -> evaluateOnceTracedSync(pdp, sub, subscriptionId))
                    .orElseGet(() -> tracedNoConfiguration(pdpId));
            fireInterceptors(tracedVote, subscriptionId, sub);
            return tracedVote;
        } finally {
            invokeOnUnsubscribe(subscriptionId);
        }
    }

    private TracedVote evaluateOnceTracedSync(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> TracedVote.of(v, clock.instant());
        case PureVoter p   -> TracedVote.of(p.vote(baseCtx), clock.instant());
        case StreamVoter s -> evaluateStreamingTracedSync(pdp, baseCtx, subscriptionId, s);
        };
    }

    private TracedVote evaluateStreamingTracedSync(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return TracedVote.of(
                    initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote(),
                    clock.instant());
        }
        if (initial.vote() != null) {
            return TracedVote.of(initial.vote(), clock.instant());
        }
        try {
            return Voters.awaitFirstTracedVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)), clock);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return TracedVote.of(errorVote(pdp, ERROR_INTERRUPTED), clock.instant());
        }
    }

    private Stream<Vote> voteStream(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId   = idFactory.newRandom();
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            return singleton(noConfigurationVote(pdpId));
        }
        return voteStream(pdpConfiguration.get(), sub, subscriptionId);
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
        val handle = attributeStore.open(subscriptionId, initial.dependencies().keySet(), snapshot -> {
            val r = voter.evaluate(baseCtx.withSnapshot(snapshot));
            if (r.vote() != null) {
                out.put(r.vote());
            }
            return r.dependencies().keySet();
        });
        out.onClose(handle::close);
        return out;
    }

    private Stream<TracedVote> tracedVoteStream(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        invokeOnSubscribe(subscriptionId, sub, pdpId);
        val                pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        Stream<TracedVote> tracedStream;
        if (pdpConfiguration.isEmpty()) {
            tracedStream = singleton(tracedNoConfiguration(pdpId));
        } else {
            tracedStream = tracedVoteStream(pdpConfiguration.get(), sub, subscriptionId);
        }
        return interceptingTracedStream(tracedStream, subscriptionId, sub);
    }

    private Stream<TracedVote> tracedVoteStream(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> singleton(TracedVote.of(v, clock.instant()));
        case PureVoter p   -> singleton(TracedVote.of(p.vote(baseCtx), clock.instant()));
        case StreamVoter s -> streamingTracedVoteStream(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Stream<TracedVote> streamingTracedVoteStream(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        val out     = new LatestSlotStream<TracedVote>();
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            val v = initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote();
            out.put(TracedVote.of(v, clock.instant()));
            out.complete();
            return out;
        }
        if (initial.vote() != null) {
            out.put(TracedVote.of(initial.vote(), clock.instant()));
        }
        val handle = attributeStore.open(subscriptionId, initial.dependencies().keySet(), snapshot -> {
            val r = voter.evaluate(baseCtx.withSnapshot(snapshot));
            if (r.vote() != null) {
                out.put(buildTracedVote(r, snapshot));
            }
            return r.dependencies().keySet();
        });
        out.onClose(handle::close);
        return out;
    }

    private TracedVote buildTracedVote(VoteResult result, Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        return new TracedVote(result.vote(), clock.instant(), result.dependencies(),
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
        val handle = attributeStore.open(subscriptionId, initialDeps, snapshot -> {
            val newDeps = new HashSet<SubscriptionKey>();
            val multi   = evaluateRound(items, pdp, subscriptionId, snapshot, newDeps);
            if (multi != null) {
                out.put(multi);
            }
            return newDeps.isEmpty() ? initialDeps : newDeps;
        });
        out.onClose(handle::close);
        return out;
    }

    private MultiAuthorizationDecision evaluateRound(List<IdentifiableAuthorizationSubscription> items, CompiledPdp pdp,
            String subscriptionId, Map<SubscriptionKey, AttributeSnapshot> snapshot,
            Set<SubscriptionKey> depsAccumulator) {
        val multi = new MultiAuthorizationDecision();
        for (val item : items) {
            val ctx = evaluationContext(pdp, item.subscription(), subscriptionId).withSnapshot(snapshot);
            val r   = evaluateVoter(pdp.voter(), ctx);
            depsAccumulator.addAll(r.dependencies().keySet());
            if (r.vote() == null) {
                return null;
            }
            if (hasInterceptors) {
                fireInterceptors(buildTracedVote(r, snapshot), item.subscriptionId(), item.subscription());
            }
            multi.setDecision(item.subscriptionId(), r.vote().authorizationDecision());
        }
        return multi;
    }

    private static VoteResult evaluateVoter(Voter voter, EvaluationContext ctx) {
        return switch (voter) {
        case Vote v        -> new VoteResult(v, Map.of());
        case PureVoter p   -> new VoteResult(p.vote(ctx), Map.of());
        case StreamVoter s -> s.evaluate(ctx);
        };
    }

    private Stream<IdentifiableAuthorizationDecision> identifiableChangeStream(
            Stream<MultiAuthorizationDecision> source, AtomicReference<MultiAuthorizationDecision> previousRef) {
        val out  = new QueueStream<IdentifiableAuthorizationDecision>();
        val pump = Thread.startVirtualThread(() -> {
                     try {
                         while (true) {
                             val current = source.awaitNext();
                             if (current == null) {
                                 return;
                             }
                             val previous = previousRef.getAndSet(current);
                             for (val identifiable : current) {
                                 val prevDecision = previous == null ? null
                                         : previous.getDecision(identifiable.subscriptionId());
                                 if (!java.util.Objects.equals(prevDecision, identifiable.decision())) {
                                     out.put(identifiable);
                                 }
                             }
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

    private Stream<TracedVote> interceptingTracedStream(Stream<TracedVote> source, String subscriptionId,
            AuthorizationSubscription sub) {
        val out  = new LatestSlotStream<TracedVote>();
        val pump = Thread.startVirtualThread(() -> {
                     try {
                         while (true) {
                             val tv = source.awaitNext();
                             if (tv == null) {
                                 invokeOnUnsubscribe(subscriptionId);
                                 return;
                             }
                             fireInterceptors(tv, subscriptionId, sub);
                             out.put(tv);
                         }
                     } catch (InterruptedException expected) {
                         Thread.currentThread().interrupt();
                         invokeOnUnsubscribe(subscriptionId);
                     } finally {
                         out.complete();
                         source.close();
                     }
                 });
        out.onClose(() -> {
            pump.interrupt();
            source.close();
            invokeOnUnsubscribe(subscriptionId);
        });
        return out;
    }

    private EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        return EvaluationContext.of(pdp.metadata().pdpId(), pdp.metadata().configurationId(), subscriptionId, sub,
                pdpConfigurationSource.getFunctionBroker());
    }

    private Vote noConfigurationVote(String pdpId) {
        val metadata = new PdpVoterMetadata("no-configuration", pdpId, "none", null, Outcome.PERMIT_OR_DENY, false);
        return Vote.error(new ErrorValue(ERROR_NO_PDP_CONFIGURATION), metadata);
    }

    private TracedVote tracedNoConfiguration(String pdpId) {
        return TracedVote.of(noConfigurationVote(pdpId), clock.instant());
    }

    private Vote errorVote(CompiledPdp pdp, String message) {
        return Vote.error(new ErrorValue(message), pdp.metadata());
    }

    private void invokeOnSubscribe(String subscriptionId, AuthorizationSubscription sub, String pdpId) {
        for (val interceptor : interceptors) {
            try {
                interceptor.onSubscribe(subscriptionId, sub, pdpId);
            } catch (Throwable swallowed) {
                // Interceptors are observability concerns, not obligations: a
                // misbehaving interceptor must not affect authorization. The
                // interceptor handles its own failure logging.
            }
        }
    }

    private void invokeOnUnsubscribe(String subscriptionId) {
        for (val interceptor : interceptors) {
            try {
                interceptor.onUnsubscribe(subscriptionId);
            } catch (Throwable swallowed) {
                // see invokeOnSubscribe.
            }
        }
    }

    private void fireInterceptors(TracedVote vote, String subscriptionId, AuthorizationSubscription sub) {
        for (val interceptor : interceptors) {
            try {
                interceptor.intercept(vote, subscriptionId, sub);
            } catch (Throwable swallowed) {
                // see invokeOnSubscribe.
            }
        }
    }
}
