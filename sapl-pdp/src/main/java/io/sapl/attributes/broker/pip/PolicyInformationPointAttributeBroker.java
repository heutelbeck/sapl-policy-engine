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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.DispatchCoalescer;
import io.sapl.documentation.LibraryDocumentationExtractor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-memory {@link AttributeBroker} backed by a runtime catalog of
 * Policy Information Points, with an optional
 * {@link AttributeRepository} fallback for invocations that have no
 * matching PIP.
 * <p>
 * Two surfaces on one object: consumers use the {@link AttributeBroker}
 * interface (open / close, snapshot callbacks); plugin engines and
 * tests use {@link #load}, {@link #swap}, {@link PipHandle#unload}
 * via the returned {@link PipHandle} to change the catalog at
 * runtime.
 * <p>
 * Catalog mutations are atomic. A failed {@link #load} or
 * {@link #swap} leaves the catalog unchanged; the collision rule
 * (no two specs of the same fully-qualified name and parameter
 * shape) is enforced at load time so resolution at evaluation time
 * is unambiguous.
 * <p>
 * Routing is fixed for each active invocation when it is created.
 * A PIP match produces an {@link ActivePolicyInformationPointInvocation}
 * that observes the PIP exclusively. A non-match produces an
 * {@link ActiveRepositoryInvocation} that observes the fallback. No
 * fallback yields a terminal UNDEFINED active invocation. The PIP is
 * authoritative for its invocations. A PIP's UNDEFINED is propagated
 * as UNDEFINED, never silently replaced by the fallback.
 * <p>
 * Catalog mutations migrate routing as needed. A {@link #swap} that
 * keeps a spec rebinds the existing active invocation in place. A
 * {@link #swap} or {@link PipHandle#unload} that removes a spec
 * migrates the active invocation to the fallback, or terminates it
 * with UNDEFINED when no fallback is configured. A {@link #load}
 * that adds a spec promotes any matching repository-backed or
 * terminal active invocations to PIP-backed ones.
 *
 * @since 4.1.0
 */
@Slf4j
public final class PolicyInformationPointAttributeBroker implements AttributeBroker {

    private static final String ERROR_ANNOTATION_MISSING      = "PIP class %s is not annotated with @PolicyInformationPoint.";
    private static final String ERROR_CATALOG_INVARIANT       = "Catalog invariant violated. Multiple exact matches for '{}'. Falling back to UNDEFINED or repository. The collision rule should have prevented this. Indicates an engine bug.";
    private static final String ERROR_DEPS_EMPTY              = "initialDependencies must not be empty";
    private static final String ERROR_GRACE_DURATION_NEGATIVE = "gracePeriodDuration must not be negative";
    private static final String ERROR_HANDLE_NOT_LOADED       = "Cannot swap PIP: handle for '%s' is not currently loaded.";
    private static final String ERROR_LOAD_PROCESSOR_FAILED   = "Failed to register PIP '%s': %s";
    private static final String ERROR_PIP_INVOKE_FAILED       = "PIP invocation for attribute '%s' failed: %s";
    private static final String ERROR_RETURNED_DEPS_INVALID   = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SPEC_COLLISION          = "Cannot register PIP '%s': attribute '%s' (parameter shape %s) collides with already-registered PIP '%s'.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK   = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE  = "subscriptionId already open: %s";
    private static final String WARN_ONUPDATE_THREW           = "Consumer {} onUpdate threw: {}";

    private final ReentrantLock                                                lock                  = new ReentrantLock(
            true);
    private final Map<PipHandleImpl, List<StreamAttributeFinderSpecification>> handleSpecs           = new LinkedHashMap<>();
    private final Map<AttributeFinderInvocation, List<ActiveInvocation>>       subscriptions         = new HashMap<>();
    private final Map<ActiveInvocation, ScheduledFuture<?>>                    pendingTeardowns      = new HashMap<>();
    private final Map<String, BrokerSubscription>                              consumerSubscriptions = new HashMap<>();

    private final Duration                      gracePeriodDuration;
    private final ScheduledExecutorService      teardownScheduler;
    private final @Nullable AttributeRepository fallback;

    /** No grace period, no fallback. */
    public PolicyInformationPointAttributeBroker() {
        this(Duration.ZERO, null);
    }

    /**
     * Constructs a broker with the given grace period and no fallback.
     * Refcount-to-zero leads to teardown after the grace duration,
     * or immediately if the duration is {@link Duration#ZERO}.
     *
     * @param gracePeriodDuration teardown delay after refcount-zero;
     * {@link Duration#ZERO} disables grace
     */
    public PolicyInformationPointAttributeBroker(@NonNull Duration gracePeriodDuration) {
        this(gracePeriodDuration, null);
    }

    /**
     * Constructs a broker with a fallback. Invocations that have no
     * matching PIP are served by {@code fallback} (typically an
     * {@link io.sapl.attributes.broker.repository.InMemoryAttributeRepository}).
     * PIP-served invocations are served exclusively by the PIP.
     * Runtime UNDEFINED from a PIP is propagated as UNDEFINED, never
     * silently replaced by the fallback's value. Routing is decided
     * once when an active invocation is created and re-evaluated only
     * on catalog mutations ({@link #load}, {@link #swap},
     * {@link PipHandle#unload}).
     *
     * @param gracePeriodDuration teardown delay after refcount-zero;
     * {@link Duration#ZERO} disables grace
     * @param fallback repository that serves invocations with no
     * matching PIP; {@code null} means consumers see UNDEFINED for
     * unmatched invocations
     */
    public PolicyInformationPointAttributeBroker(@NonNull Duration gracePeriodDuration,
            @Nullable AttributeRepository fallback) {
        if (gracePeriodDuration.isNegative()) {
            throw new IllegalArgumentException(ERROR_GRACE_DURATION_NEGATIVE);
        }
        this.gracePeriodDuration = gracePeriodDuration;
        this.fallback            = fallback;
        this.teardownScheduler   = Executors.newSingleThreadScheduledExecutor(runnable -> {
                                     val thread = Thread.ofVirtual().unstarted(runnable);
                                     thread.setName("PolicyInformationPointAttributeBroker-teardown");
                                     return thread;
                                 });
    }

    /**
     * Loads the given PIP instance and registers all of its annotated
     * attribute methods. Atomic: either every spec the instance
     * contributes is registered and a handle is returned, or the call
     * throws {@link PipLoadException} and the catalog is unchanged.
     *
     * @param pipInstance a fully-constructed PIP instance whose class
     * is annotated with {@link PolicyInformationPoint}
     * @return a handle the caller may use to unload or swap this PIP
     * @throws PipLoadException if the class is not annotated, if any
     * annotated method has an invalid signature, or if any of the
     * instance's specs collides with an already-registered spec
     */
    public PipHandle load(@NonNull Object pipInstance) {
        val namespace = readNamespace(pipInstance);
        log.debug("Loading PIP '{}' from class {}", namespace, pipInstance.getClass().getName());

        val newSpecs      = extractSpecs(pipInstance, namespace);
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(pipInstance.getClass());

        PipHandleImpl handle;
        val           promotions = new LinkedHashMap<ActiveInvocation, StreamAttributeFinderSpecification>();
        lock.lock();

        try {
            checkCollisions(namespace, newSpecs, null);
            handle = new PipHandleImpl(namespace, documentation);
            handleSpecs.put(handle, List.copyOf(newSpecs));
            for (val active : allActive()) {
                if (active.sourceSpec() != null) {
                    continue;
                }
                val match = resolve(active.invocation());
                match.ifPresent(spec -> promotions.put(active, spec));
            }
            log.debug("Loaded PIP '{}' with {} attribute(s)", namespace, newSpecs.size());
        } finally {

            lock.unlock();

        }
        for (val entry : promotions.entrySet()) {
            val old         = entry.getKey();
            val replacement = newActivePipInvocation(old.invocation(), entry.getValue());
            migrate(old, replacement);
        }
        return handle;
    }

    /**
     * Atomically replaces the specs registered by {@code oldHandle}
     * with the specs contributed by {@code newInstance}. Active
     * invocations whose source resolved through {@code oldHandle}
     * are rebound to the matching new spec without publishing a
     * transient value to their consumers. Specs that exist in
     * {@code oldHandle} but have no shape match in
     * {@code newInstance} are evicted. Their active invocations
     * migrate to the fallback when one is configured, otherwise they
     * publish {@link Value#UNDEFINED} and close. Specs new in
     * {@code newInstance} are available for future invocations.
     * <p>
     * Atomic: either the swap completes or the call throws and the
     * catalog still reflects {@code oldHandle}.
     *
     * @param oldHandle the handle to replace (must currently be
     * loaded)
     * @param newInstance the replacement PIP instance
     * @return a fresh handle for {@code newInstance}; {@code oldHandle}
     * is marked unloaded
     * @throws PipLoadException on the same conditions as
     * {@link #load}, plus when {@code oldHandle} is not currently
     * loaded
     */
    public PipHandle swap(@NonNull PipHandle oldHandle, @NonNull Object newInstance) {
        if (!(oldHandle instanceof PipHandleImpl old) || old.broker() != this) {
            throw new PipLoadException(ERROR_HANDLE_NOT_LOADED.formatted(oldHandle.pipName()));
        }
        val newNamespace = readNamespace(newInstance);
        log.debug("Swapping PIP '{}' -> '{}' (new class {})", old.pipName(), newNamespace,
                newInstance.getClass().getName());

        val newSpecs         = extractSpecs(newInstance, newNamespace);
        val newDocumentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(newInstance.getClass());

        SwapPlan      plan;
        Affected      affected;
        PipHandleImpl newHandle;

        lock.lock();

        try {
            val oldSpecs = handleSpecs.get(old);
            if (oldSpecs == null || !old.loaded.get()) {
                throw new PipLoadException(ERROR_HANDLE_NOT_LOADED.formatted(old.pipName()));
            }
            checkCollisions(newNamespace, newSpecs, oldSpecs);

            plan = buildSwapPlan(oldSpecs, newSpecs);

            handleSpecs.remove(old);
            old.loaded.set(false);
            newHandle = new PipHandleImpl(newNamespace, newDocumentation);
            handleSpecs.put(newHandle, List.copyOf(newSpecs));

            affected = collectAffected(plan);
        } finally {

            lock.unlock();

        }

        // Outside the lock: rebind / evict. invoke(...) on the new spec may throw or
        // block.
        val fb = fallback;
        for (val entry : affected.swapped.entrySet()) {
            rebind(entry.getKey(), entry.getValue());
        }
        for (val active : affected.evicted) {
            if (fb != null) {
                migrate(active, newActiveRepositoryInvocation(active.invocation(), fb));
            } else {
                discard(active);
            }
        }

        log.debug("Swapped PIP '{}': {} attribute(s) rebound, {} evicted, {} added", newNamespace, plan.swapped.size(),
                plan.evictedOnly.size(), plan.addedOnly.size());
        return newHandle;
    }

    private static SwapPlan buildSwapPlan(List<StreamAttributeFinderSpecification> oldSpecs,
            List<StreamAttributeFinderSpecification> newSpecs) {
        val swapped     = new LinkedHashMap<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification>();
        val evictedOnly = new ArrayList<StreamAttributeFinderSpecification>();
        val addedOnly   = new ArrayList<>(newSpecs);
        for (val oldSpec : oldSpecs) {
            val match = findShapeMatch(oldSpec, newSpecs);
            if (match != null) {
                swapped.put(oldSpec, match);
                addedOnly.remove(match);
            } else {
                evictedOnly.add(oldSpec);
            }
        }
        return new SwapPlan(swapped, evictedOnly, addedOnly);
    }

    private Affected collectAffected(SwapPlan plan) {
        val swapped = new LinkedHashMap<ActivePolicyInformationPointInvocation, StreamAttributeFinderSpecification>();
        val evicted = new ArrayList<ActivePolicyInformationPointInvocation>();
        for (val active : allActive()) {
            if (!(active instanceof ActivePolicyInformationPointInvocation pip)) {
                continue;
            }
            val oldSpec = pip.sourceSpec();
            if (oldSpec == null) {
                continue;
            }
            val newSpec = plan.swapped.get(oldSpec);
            if (newSpec != null) {
                swapped.put(pip, newSpec);
            } else if (plan.evictedOnly.contains(oldSpec)) {
                evicted.add(pip);
            }
        }
        return new Affected(swapped, evicted);
    }

    private record SwapPlan(
            Map<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification> swapped,
            List<StreamAttributeFinderSpecification> evictedOnly,
            List<StreamAttributeFinderSpecification> addedOnly) {}

    private record Affected(
            Map<ActivePolicyInformationPointInvocation, StreamAttributeFinderSpecification> swapped,
            List<ActivePolicyInformationPointInvocation> evicted) {}

    /**
     * @return an unmodifiable snapshot of currently loaded handles
     */
    public Set<PipHandle> catalog() {
        lock.lock();

        try {
            return Set.copyOf(handleSpecs.keySet());
        } finally {

            lock.unlock();

        }
    }

    /**
     * @return the {@link LibraryDocumentation} for every PIP currently
     * in the catalog, suitable for IDE hover/completion and offline
     * documentation generation. Returned in load order; reflects the
     * catalog at the moment of the call.
     */
    public List<LibraryDocumentation> documentation() {
        lock.lock();

        try {
            val docs = new ArrayList<LibraryDocumentation>(handleSpecs.size());
            for (val handle : handleSpecs.keySet()) {
                docs.add(handle.documentation());
            }
            return List.copyOf(docs);
        } finally {

            lock.unlock();

        }
    }

    /**
     * Resolves an invocation to its serving spec. By the catalog's
     * collision rule, at most one spec matches. Visible mainly for
     * tests; consumers normally exercise resolution implicitly via
     * {@link #open}.
     *
     * @param invocation the policy-driven invocation
     * @return the matching spec, or {@link Optional#empty()} if no
     * loaded PIP serves this invocation
     */
    public Optional<StreamAttributeFinderSpecification> resolve(@NonNull AttributeFinderInvocation invocation) {
        lock.lock();

        try {
            StreamAttributeFinderSpecification exact   = null;
            StreamAttributeFinderSpecification varargs = null;
            for (val specs : handleSpecs.values()) {
                for (val spec : specs) {
                    val match = spec.matches(invocation);
                    if (match == Match.EXACT_MATCH) {
                        if (exact != null) {
                            log.error(ERROR_CATALOG_INVARIANT, invocation.attributeName());
                            return Optional.empty();
                        }
                        exact = spec;
                    } else if (match == Match.VARARGS_MATCH && varargs == null) {
                        varargs = spec;
                    }
                }
            }
            if (exact != null) {
                return Optional.of(exact);
            }
            return Optional.ofNullable(varargs);
        } finally {

            lock.unlock();

        }
    }

    @Override
    public Subscription open(@NonNull String subscriptionId, @NonNull Set<SubscriptionKey> initialDependencies,
            @NonNull Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        if (subscriptionId.isBlank()) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_BLANK);
        }
        if (initialDependencies.isEmpty()) {
            throw new IllegalArgumentException(ERROR_DEPS_EMPTY);
        }

        BrokerSubscription subscription;
        boolean            fireImmediately;
        lock.lock();

        try {
            if (consumerSubscriptions.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            subscription = new BrokerSubscription(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            for (val key : initialDependencies) {
                // Get the active invocation for this key, reusing an existing one if possible.
                val activeInvocation = activeInvocationFor(key);
                // Record the route so dispatch can find the active invocation for this key.
                subscription.route.put(key, activeInvocation);
                // Attach this subscription to the active invocation for refcount bookkeeping.
                activeInvocation.attach(subscription);
            }
            // Register the subscription in the broker's consumer index.
            consumerSubscriptions.put(subscriptionId, subscription);
            // If every dep is already in its active invocation's mailbox (warm-attach),
            // open the gate and fire synchronously with the cached snapshot.
            fireImmediately = subscription.tryFireGate();
        } finally {

            lock.unlock();

        }
        log.trace("Opened subscription '{}' with {} dependency(ies)", subscriptionId, initialDependencies.size());
        if (fireImmediately) {
            subscription.fireCallback();
        }
        return subscription;
    }

    @Override
    public void close() {
        List<BrokerSubscription>     toClose;
        Collection<ActiveInvocation> activeToClose;
        lock.lock();

        try {
            toClose       = new ArrayList<>(consumerSubscriptions.values());
            activeToClose = new ArrayList<>(allActive());
            consumerSubscriptions.clear();
            subscriptions.clear();
            for (val task : pendingTeardowns.values()) {
                task.cancel(false);
            }
            pendingTeardowns.clear();
        } finally {

            lock.unlock();

        }
        for (val c : toClose) {
            c.markClosed();
        }
        for (val a : activeToClose) {
            a.close();
        }
        teardownScheduler.shutdownNow();
    }

    private static String readNamespace(Object instance) {
        val annotation = instance.getClass().getAnnotation(PolicyInformationPoint.class);
        if (annotation == null) {
            throw new PipLoadException(ERROR_ANNOTATION_MISSING.formatted(instance.getClass().getName()));
        }
        val declared = annotation.name();
        if (declared == null || declared.isBlank()) {
            return instance.getClass().getSimpleName();
        }
        return declared;
    }

    private static List<StreamAttributeFinderSpecification> extractSpecs(Object instance, String namespace) {
        val specs = new ArrayList<StreamAttributeFinderSpecification>();
        for (Method method : instance.getClass().getMethods()) {
            if (!method.isAnnotationPresent(Attribute.class)
                    && !method.isAnnotationPresent(EnvironmentAttribute.class)) {
                continue;
            }
            try {
                val spec = StreamAttributeMethodSignatureProcessor.processAttributeMethod(instance, namespace, method);
                if (spec != null) {
                    specs.add(spec);
                    log.trace("Processed attribute '{}' ({} fixed parameter(s){})", spec.fullyQualifiedName(),
                            spec.parameterTypes().size(), spec.hasVariableNumberOfArguments() ? " plus varargs" : "");
                }
            } catch (RuntimeException e) {
                val attrFqn = namespace + '.' + method.getName();
                log.debug("Failed to process attribute method '{}': {}", attrFqn, e.getMessage());
                throw new PipLoadException(ERROR_LOAD_PROCESSOR_FAILED.formatted(attrFqn, e.getMessage()), e);
            }
        }
        return specs;
    }

    /**
     * Caller holds the broker lock.
     */
    private void checkCollisions(String namespace, List<StreamAttributeFinderSpecification> newSpecs,
            List<StreamAttributeFinderSpecification> excludeFromCheck) {
        for (val candidate : newSpecs) {
            for (val entry : handleSpecs.entrySet()) {
                val existingSpecs = entry.getValue();
                if (existingSpecs == excludeFromCheck) {
                    continue;
                }
                for (val existing : existingSpecs) {
                    if (candidate.collidesWith(existing)) {
                        throw new PipLoadException(ERROR_SPEC_COLLISION.formatted(namespace, candidate.attributeName(),
                                shapeOf(candidate), entry.getKey().pipName()));
                    }
                }
            }
        }
    }

    private static StreamAttributeFinderSpecification findShapeMatch(StreamAttributeFinderSpecification needle,
            List<StreamAttributeFinderSpecification> candidates) {
        for (val candidate : candidates) {
            if (sameShape(needle, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean sameShape(StreamAttributeFinderSpecification a, StreamAttributeFinderSpecification b) {
        return a.fullyQualifiedName().equals(b.fullyQualifiedName())
                && a.isEnvironmentAttribute() == b.isEnvironmentAttribute()
                && a.parameterTypes().size() == b.parameterTypes().size()
                && a.hasVariableNumberOfArguments() == b.hasVariableNumberOfArguments();
    }

    private static String shapeOf(StreamAttributeFinderSpecification spec) {
        val arity = spec.parameterTypes().size();
        return spec.hasVariableNumberOfArguments() ? arity + "+ varargs" : String.valueOf(arity);
    }

    /**
     * Caller holds the broker lock.
     * <p>
     * {@code fresh=true}: always creates a new active invocation and
     * appends it to the per-invocation list. {@code fresh=false}:
     * attaches to the first live entry in that list, cancelling any
     * pending teardown on it (warm reconnect during the grace window).
     * <p>
     * The list is keyed by the normalized invocation, which drops the
     * {@code fresh} flag. So fresh and non-fresh subscriptions for an
     * otherwise-identical invocation land in the same list. A fresh
     * stream stays private while it's alive, but a later non-fresh
     * consumer can attach to it once the original head has been torn
     * down.
     */
    private ActiveInvocation activeInvocationFor(SubscriptionKey key) {
        val invocation = key.invocation();
        // Normalize to ignore the "fresh" flag for deduping. Ugly but functional.
        val normalizedInvocation = normalizeInvocation(invocation);
        val list                 = subscriptions.computeIfAbsent(normalizedInvocation, k -> new ArrayList<>());
        if (!invocation.fresh()) {
            // Attempt to reuse an existing active invocation.
            for (val candidate : list) {
                // Defensive. Every tear-down path removes from this list under the broker
                // lock before calling close(), so a candidate found here should never be
                // closed. Inexpensive safety net for that invariant.
                if (!candidate.isClosed()) {
                    cancelPendingTeardown(candidate);
                    return candidate;
                }
            }
        }
        // No reusable entry or a fresh stream was explicitly requested. Create one.
        val active = activateInvocationFor(normalizedInvocation);
        // Add to per-invocation list so future non-fresh consumers can attach to it.
        list.add(active);
        return active;
    }

    /**
     * Returns the invocation with its {@code fresh} flag zeroed. Used
     * as the dedup-map key so consumers with different {@code fresh}
     * values for the same underlying attribute share a per-invocation
     * list.
     */
    private static AttributeFinderInvocation normalizeInvocation(AttributeFinderInvocation invocation) {
        if (!invocation.fresh()) {
            return invocation;
        }
        return new AttributeFinderInvocation(invocation.configurationId(), invocation.attributeName(),
                invocation.entity(), invocation.arguments(), invocation.initialTimeOut(), invocation.pollInterval(),
                invocation.backoff(), invocation.retries(), false, invocation.ctx());
    }

    /**
     * Caller holds the broker lock.
     * <p>
     * Routes by catalog state at activation time. A PIP match yields
     * an {@link ActivePolicyInformationPointInvocation} fed by an
     * {@link AttributeStream} (poll / retry / timeout cycle around
     * the PIP). No PIP match and a configured fallback yields an
     * {@link ActiveRepositoryInvocation} observing the fallback. No
     * PIP match and no fallback yields a terminal active invocation
     * preloaded with {@link Value#UNDEFINED}.
     * <p>
     * The PIP receives the normalized invocation (with {@code fresh}
     * zeroed). {@code fresh} is a broker-internal flag and never
     * leaves this layer.
     */
    private ActiveInvocation activateInvocationFor(AttributeFinderInvocation invocation) {
        val maybeAttributeFinderSpec = resolve(invocation);
        if (maybeAttributeFinderSpec.isPresent()) {
            val activeInvocation = newActivePipInvocation(invocation, maybeAttributeFinderSpec.get());
            activeInvocation.start();
            return activeInvocation;
        }
        val fb = fallback;
        if (fb != null) {
            val active = newActiveRepositoryInvocation(invocation, fb);
            active.start();
            return active;
        }
        return newTerminalActiveInvocation(invocation);
    }

    private ActivePolicyInformationPointInvocation newActivePipInvocation(AttributeFinderInvocation normalized,
            StreamAttributeFinderSpecification spec) {
        val                     holder    = new ActivePolicyInformationPointInvocation[1];
        Supplier<Stream<Value>> innerOpen = openSyncFirstThenLazy(innerStreamSupplier(spec, normalized));
        val                     stream    = new AttributeStream(normalized, innerOpen);
        holder[0] = new ActivePolicyInformationPointInvocation(normalized, stream, spec, v -> dispatchValue(holder[0]));
        return holder[0];
    }

    /**
     * Wraps {@code supplier} so the first call returns a stream opened
     * synchronously on the current (broker) thread, and every
     * subsequent call delegates to {@code supplier} directly. The
     * synchronous first open lets PIPs register subscribers, open
     * connections, and take other setup side effects before this
     * method returns, so values emitted on the PIP side land on a
     * registered subscriber rather than being dropped.
     * <p>
     * The wrapper is {@link AutoCloseable} so the consumer (the
     * AttributeStream) can release the synchronously-opened stream
     * if it is closed before the pump consumed it. Without this,
     * fast activate-then-deactivate churn leaks PIP streams nobody
     * drains.
     */
    private static Supplier<Stream<Value>> openSyncFirstThenLazy(Supplier<Stream<Value>> supplier) {
        return new SyncFirstThenLazySupplier(supplier);
    }

    private static final class SyncFirstThenLazySupplier implements Supplier<Stream<Value>>, AutoCloseable {

        private final Supplier<Stream<Value>>        delegate;
        private final AtomicReference<Stream<Value>> first;

        SyncFirstThenLazySupplier(Supplier<Stream<Value>> delegate) {
            this.delegate = delegate;
            this.first    = new AtomicReference<>(delegate.get());
        }

        @Override
        public Stream<Value> get() {
            val pre = first.getAndSet(null);
            return pre != null ? pre : delegate.get();
        }

        @Override
        public void close() {
            val unclaimed = first.getAndSet(null);
            if (unclaimed != null) {
                try {
                    unclaimed.close();
                } catch (RuntimeException ignored) {
                    // best-effort cleanup of an abandoned synchronous open
                }
            }
        }
    }

    private ActiveRepositoryInvocation newActiveRepositoryInvocation(AttributeFinderInvocation normalized,
            AttributeRepository repository) {
        val holder = new ActiveRepositoryInvocation[1];
        holder[0] = new ActiveRepositoryInvocation(normalized, repository, v -> dispatchValue(holder[0]));
        return holder[0];
    }

    private ActivePolicyInformationPointInvocation newTerminalActiveInvocation(AttributeFinderInvocation normalized) {
        val holder = new ActivePolicyInformationPointInvocation[1];
        holder[0] = new ActivePolicyInformationPointInvocation(normalized, null, null, v -> dispatchValue(holder[0]));
        holder[0].publishImmediate(Value.UNDEFINED);
        return holder[0];
    }

    /**
     * Replaces {@code old} with {@code replacement} in the
     * per-invocation list and in every subscribed consumer's routing
     * table. Subscriber refcount is preserved across the swap. Starts
     * {@code replacement} and closes {@code old} after the state move
     * is committed.
     * <p>
     * Used by catalog mutations to re-route active invocations when
     * their source changes. {@link #load} promotes repository-backed
     * or terminal active invocations to PIP-backed ones. {@link #swap}
     * or {@link PipHandle#unload} demotes PIP-backed active
     * invocations whose spec is gone to repository-backed or terminal.
     */
    private void migrate(ActiveInvocation old, ActiveInvocation replacement) {
        lock.lock();

        try {
            val list = subscriptions.get(old.invocation());
            if (list != null) {
                val idx = list.indexOf(old);
                if (idx >= 0) {
                    list.set(idx, replacement);
                } else {
                    list.add(replacement);
                }
            } else {
                subscriptions.computeIfAbsent(old.invocation(), k -> new ArrayList<>()).add(replacement);
            }
            cancelPendingTeardown(old);
            val subscribersSnapshot = new ArrayList<>(old.subscribers());
            for (val consumer : subscribersSnapshot) {
                for (val entry : consumer.route.entrySet()) {
                    if (entry.getValue() == old) {
                        entry.setValue(replacement);
                        old.detach(consumer);
                        replacement.attach(consumer);
                    }
                }
            }
        } finally {

            lock.unlock();

        }
        replacement.start();
        old.close();
    }

    /**
     * Builds the inner-stream supplier that {@link AttributeStream}
     * calls at the start of each cycle. Wraps invoke-time
     * {@link RuntimeException}s as an inline error stream so the
     * retry/burst logic in {@link AttributeStream} handles them
     * uniformly with mid-stream errors.
     */
    private static Supplier<Stream<Value>> innerStreamSupplier(StreamAttributeFinderSpecification spec,
            AttributeFinderInvocation invocation) {
        return () -> {
            try {
                return spec.attributeFinder().invoke(invocation);
            } catch (RuntimeException e) {
                log.debug("Attribute finder for '{}' threw on invoke: {}", invocation.attributeName(), e.getMessage());
                return Streams.just(
                        Value.error(ERROR_PIP_INVOKE_FAILED.formatted(invocation.attributeName(), e.getMessage())));
            }
        };
    }

    /**
     * Returns a snapshot of every live active invocation across all
     * per-invocation lists. Caller holds the broker lock.
     */
    private List<ActiveInvocation> allActive() {
        val all = new ArrayList<ActiveInvocation>();
        for (val list : subscriptions.values()) {
            all.addAll(list);
        }
        return all;
    }

    private void rebind(ActivePolicyInformationPointInvocation active, StreamAttributeFinderSpecification newSpec) {
        val           supplier  = openSyncFirstThenLazy(innerStreamSupplier(newSpec, active.invocation()));
        Stream<Value> newStream = new AttributeStream(active.invocation(), supplier);
        active.rebind(newStream, newSpec);
    }

    /**
     * Publishes UNDEFINED to the active invocation's mailbox, removes
     * it from the per-invocation list and any pending teardown
     * registration, and closes it. Used by catalog mutations that
     * remove the serving PIP when no fallback exists.
     */
    private void discard(ActivePolicyInformationPointInvocation active) {
        active.publishImmediate(Value.UNDEFINED);
        lock.lock();

        try {
            removeFromList(active);
            cancelPendingTeardown(active);
        } finally {

            lock.unlock();

        }
        active.close();
    }

    /**
     * Caller holds the broker lock. Removes the active invocation
     * from its invocation's list, dropping the list itself when it
     * becomes empty.
     */
    private void removeFromList(ActiveInvocation activeInvocation) {
        val list = subscriptions.get(activeInvocation.invocation());
        if (list == null) {
            return;
        }
        list.remove(activeInvocation);
        if (list.isEmpty()) {
            subscriptions.remove(activeInvocation.invocation());
        }
    }

    /**
     * Caller holds the broker lock. Cancels a scheduled grace-period
     * teardown for {@code activeInvocation}, if one is pending.
     */
    private void cancelPendingTeardown(ActiveInvocation activeInvocation) {
        val task = pendingTeardowns.remove(activeInvocation);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Called when an active invocation's refcount drops to zero.
     * <p>
     * Three cases:
     * <ul>
     * <li>The per-invocation list has more than one entry: tear
     * down immediately. The other entries already serve future
     * {@code fresh=false} arrivals, so a grace window here would
     * just delay reclaiming the resource.</li>
     * <li>Grace duration is zero: tear down immediately.</li>
     * <li>Otherwise: schedule teardown after the grace duration.
     * The active invocation stays in the list during the window. A
     * re-attaching {@code fresh=false} consumer cancels the
     * scheduled teardown and reuses the warm connection.</li>
     * </ul>
     */
    private void handleRefcountZero(ActiveInvocation activeInvocation) {
        lock.lock();

        try {
            if (activeInvocation.refcount() > 0) {
                return;
            }
            val list        = subscriptions.get(activeInvocation.invocation());
            val teardownNow = (list != null && list.size() > 1) || gracePeriodDuration.isZero();
            if (!teardownNow) {
                scheduleTeardown(activeInvocation);
                return;
            }
            removeFromList(activeInvocation);
        } finally {

            lock.unlock();

        }
        activeInvocation.close();
    }

    /**
     * Caller holds the broker lock. Schedules the active invocation
     * for teardown after the configured grace duration. The task is
     * idempotent against cancellation via {@link #pendingTeardowns}.
     */
    private void scheduleTeardown(ActiveInvocation activeInvocation) {
        val task = teardownScheduler.schedule(() -> runScheduledTeardown(activeInvocation),
                gracePeriodDuration.toMillis(), TimeUnit.MILLISECONDS);
        pendingTeardowns.put(activeInvocation, task);
    }

    private void runScheduledTeardown(ActiveInvocation activeInvocation) {
        lock.lock();

        try {
            if (pendingTeardowns.remove(activeInvocation) == null) {
                return;
            }
            if (activeInvocation.refcount() > 0) {
                return;
            }
            removeFromList(activeInvocation);
        } finally {

            lock.unlock();

        }
        activeInvocation.close();
    }

    /**
     * Called by an {@link ActiveInvocation} on each value publication.
     * Iterates only this active invocation's own subscriber set (a
     * reverse index maintained by attach/detach), so dispatch is
     * O(consumers-of-this-active-invocation), not O(all-consumers).
     */
    private void dispatchValue(ActiveInvocation active) {
        List<BrokerSubscription> toFire;
        lock.lock();

        try {
            toFire = new ArrayList<>();
            for (val consumer : active.subscribers()) {
                if (consumer.tryFireGate()) {
                    toFire.add(consumer);
                }
            }
        } finally {

            lock.unlock();

        }
        for (val c : toFire) {
            c.fireCallback();
        }
    }

    void unloadHandle(PipHandleImpl handle) {
        List<StreamAttributeFinderSpecification>     evicted;
        List<ActivePolicyInformationPointInvocation> evictedActive;
        lock.lock();

        try {
            evicted = handleSpecs.remove(handle);
            if (evicted == null) {
                return;
            }
            handle.loaded.set(false);

            evictedActive = new ArrayList<>();
            for (val active : allActive()) {
                if (!(active instanceof ActivePolicyInformationPointInvocation pip)) {
                    continue;
                }
                val oldSpec = pip.sourceSpec();
                if (oldSpec != null && evicted.contains(oldSpec)) {
                    evictedActive.add(pip);
                }
            }
        } finally {

            lock.unlock();

        }
        val fb = fallback;
        for (val active : evictedActive) {
            if (fb != null) {
                migrate(active, newActiveRepositoryInvocation(active.invocation(), fb));
            } else {
                discard(active);
            }
        }
        log.debug("Unloaded PIP '{}' ({} attribute(s) removed)", handle.pipName(), evicted.size());
    }

    /**
     * Subscription handle for one consumer. Owns the per-consumer
     * routing table, the gate state, and the callback. Serializes
     * onUpdate firing through a {@link DispatchCoalescer}: rapid
     * publishes during an in-flight {@code onUpdate} collapse into one
     * re-fire afterwards, against the latest mailbox snapshot.
     */
    final class BrokerSubscription implements Subscription {

        final String                                                                  id;
        final DispatchCoalescer                                                       coalescer;
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          deps;
        Map<SubscriptionKey, ActiveInvocation>                                        route    = new HashMap<>();
        boolean                                                                       gateOpen = false;
        boolean                                                                       closed   = false;

        BrokerSubscription(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id        = id;
            this.deps      = deps;
            this.onUpdate  = onUpdate;
            this.coalescer = new DispatchCoalescer(this::runOneFire);
        }

        @Override
        public void close() {
            List<ActiveInvocation> zeroed;
            lock.lock();

            try {
                if (closed) {
                    return;
                }
                closed = true;
                consumerSubscriptions.remove(id);
                zeroed = new ArrayList<>();
                for (val active : route.values()) {
                    if (active.detach(this) <= 0) {
                        zeroed.add(active);
                    }
                }
                route.clear();
            } finally {

                lock.unlock();

            }
            for (val active : zeroed) {
                handleRefcountZero(active);
            }
            log.trace("Closed subscription '{}'", id);
        }

        void markClosed() {
            lock.lock();

            try {
                closed = true;
            } finally {

                lock.unlock();

            }
        }

        /**
         * Caller holds the broker lock. Returns {@code true} iff every
         * current dep has a value in its active invocation's mailbox.
         */
        boolean allDepsHaveValues() {
            for (val key : deps) {
                val active = route.get(key);
                if (active == null || active.snapshot().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Caller holds the broker lock. Returns {@code true} iff the
         * consumer is ready to fire: the gate is already open, or it
         * opens now because every dep has a value. The caller is
         * responsible for invoking {@link #fireCallback} after the
         * lock is released.
         */
        boolean tryFireGate() {
            if (gateOpen) {
                return true;
            }
            if (!allDepsHaveValues()) {
                return false;
            }
            gateOpen = true;
            return true;
        }

        Map<SubscriptionKey, AttributeSnapshot> currentSnapshot() {
            val now    = Instant.now();
            val result = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(deps.size());
            for (val key : deps) {
                val active = route.get(key);
                if (active == null) {
                    continue;
                }
                active.snapshot().ifPresent(value -> result.put(key, new AttributeSnapshot(value, now)));
            }
            return Map.copyOf(result);
        }

        void fireCallback() {
            coalescer.requestFire();
        }

        /**
         * Body of a single coalesced fire. Reads the current snapshot
         * under the broker lock, invokes the consumer callback outside
         * the lock, and applies the returned dep diff. See
         * {@link DispatchCoalescer} for the surrounding flag dance.
         */
        private void runOneFire() {
            Map<SubscriptionKey, AttributeSnapshot>                                 snapshot;
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> cb;
            lock.lock();

            try {
                if (closed) {
                    return;
                }
                snapshot = currentSnapshot();
                cb       = onUpdate;
            } finally {

                lock.unlock();

            }
            Set<SubscriptionKey> newDeps;
            try {
                newDeps = cb.apply(snapshot);
            } catch (RuntimeException e) {
                log.warn(WARN_ONUPDATE_THREW, id, e.getMessage(), e);
                return;
            }
            if (newDeps == null || newDeps.isEmpty()) {
                throw new IllegalStateException(ERROR_RETURNED_DEPS_INVALID.formatted(id));
            }
            applyDepDiff(newDeps);
        }

        private void applyDepDiff(Set<SubscriptionKey> newDeps) {
            List<ActiveInvocation> zeroed = new ArrayList<>();
            boolean                refire;
            lock.lock();

            try {
                if (closed) {
                    return;
                }
                if (newDeps.equals(deps)) {
                    return;
                }
                val added = new HashSet<>(newDeps);
                added.removeAll(deps);
                val removed = new HashSet<>(deps);
                removed.removeAll(newDeps);

                for (val key : added) {
                    val active = activeInvocationFor(key);
                    route.put(key, active);
                    active.attach(this);
                }
                for (val key : removed) {
                    val active = route.remove(key);
                    if (active != null && active.detach(this) <= 0) {
                        zeroed.add(active);
                    }
                }
                deps     = new HashSet<>(newDeps);
                gateOpen = allDepsHaveValues();
                refire   = gateOpen && added.stream().anyMatch(k -> route.get(k).snapshot().isPresent());
            } finally {

                lock.unlock();

            }
            for (val active : zeroed) {
                handleRefcountZero(active);
            }
            if (refire) {
                fireCallback();
            }
        }
    }

    /**
     * Internal handle implementation. Tied to its enclosing broker
     * instance so that {@code unload()} routes back to the correct
     * catalog and so that {@code swap()} can reject foreign handles.
     */
    final class PipHandleImpl implements PipHandle {

        private final String               pipName;
        private final LibraryDocumentation documentation;
        private final AtomicBoolean        loaded = new AtomicBoolean(true);

        PipHandleImpl(String pipName, LibraryDocumentation documentation) {
            this.pipName       = pipName;
            this.documentation = documentation;
        }

        @Override
        public String pipName() {
            return pipName;
        }

        @Override
        public boolean isLoaded() {
            return loaded.get();
        }

        @Override
        public LibraryDocumentation documentation() {
            return documentation;
        }

        @Override
        public void unload() {
            unloadHandle(this);
        }

        PolicyInformationPointAttributeBroker broker() {
            return PolicyInformationPointAttributeBroker.this;
        }

        @Override
        public String toString() {
            return "PipHandle[" + pipName + (loaded.get() ? "" : " unloaded") + "]";
        }
    }
}
