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

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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

import org.jspecify.annotations.Nullable;

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
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.DispatchCoalescer;
import io.sapl.attributes.broker.repository.RepositoryState;
import io.sapl.documentation.LibraryDocumentationExtractor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * In-memory {@link AttributeBroker} backed by a runtime catalog of Policy
 * Information Points, with an optional
 * {@link AttributeRepository} fallback for invocations that have no matching
 * PIP.
 * <p>
 * Two surfaces on one object. Consumers use the {@link AttributeBroker}
 * interface (open / close, snapshot callbacks).
 * Plugin engines and tests use {@link #load}, {@link #swap},
 * {@link PipHandle#unload} via the returned
 * {@link PipHandle} to change the catalog at runtime.
 * <p>
 * Catalog mutations are atomic. A failed {@link #load} or {@link #swap} leaves
 * the catalog unchanged. The collision
 * rule (no two specs of the same fully-qualified name and parameter shape) is
 * enforced at load time so resolution at
 * evaluation time is unambiguous.
 * <p>
 * Routing is fixed for each active invocation when it is created. A PIP match
 * produces an
 * {@link ActivePolicyInformationPointInvocation} that observes the PIP
 * exclusively. A non-match produces an
 * {@link ActiveRepositoryInvocation} that observes the fallback. No fallback
 * yields a terminal UNDEFINED active
 * invocation. The PIP is authoritative for its invocations. A PIP's UNDEFINED
 * is propagated as UNDEFINED, never
 * silently replaced by the fallback.
 * <p>
 * Catalog mutations migrate routing as needed. A {@link #swap} that keeps a
 * spec rebinds the existing active invocation
 * in place. A {@link #swap} or {@link PipHandle#unload} that removes a spec
 * migrates the active invocation to the
 * fallback, or terminates it with UNDEFINED when no fallback is configured. A
 * {@link #load} that adds a spec promotes
 * any matching repository-backed or terminal active invocations to PIP-backed
 * ones.
 * <p>
 * Repository values use one observation per active tenant/configuration route.
 * A callback supplies a complete {@link RepositoryState}. The broker rejects
 * callbacks from retired observations and revisions no newer than the installed
 * view. Under its routing lock it installs every affected active projection,
 * including different timing or fresh variants of the same repository key.
 * Capturing a consumer's input map uses that same lock. Thus no evaluation can
 * capture only half of a related repository update. Notifications are requested
 * after installation, outside the lock; the eventual round may use an even
 * newer view if updates coalesce.
 * <p>
 * New repository dependencies read from the installed complete view. Comparing
 * all active bindings is necessary when notifications skip intermediate
 * revisions: changes to a different key in an earlier revision still matter.
 * Binding generations distinguish renewal from an unrelated source update, so
 * unchanged keys keep their existing observation timestamps. These timestamps
 * describe observation time, not source transaction identity.
 * <p>
 * Each consumer has its own {@link DispatchCoalescer}, which serializes callbacks
 * without a dedicated thread. The subscription captures a new input map before each round.
 * Readiness is checked again at capture. First values of a new dependency set
 * are collected together. Updates of values previously delivered to this
 * consumer can start a partial round while new keys are still pending. The
 * consumer must reevaluate from the root and report an incomplete result when
 * the available inputs do not suffice. Snapshot identities distinguish accepted
 * publications from already captured notifications, including equal-value
 * renewals under a fixed clock. These identities are private observation tokens,
 * not part of policy value equality. An absent key
 * in an available repository view supplies a fulfilled {@link Value#UNDEFINED};
 * an unavailable view supplies an error value.
 * <p>
 * Source sharing uses compatible normalized invocations, not attribute names
 * alone. A fresh request creates a new active invocation instead of reusing
 * one at attachment time. A later non-fresh request can reuse that invocation;
 * fresh is not an exclusive lifetime lease or a transaction boundary. Head
 * freezing belongs to the evaluation-side cache, not this broker. Zero-reference
 * invocations can remain alive for the configured grace period, allowing a new
 * consumer to reuse them without reopening the source.
 *
 * @since 4.1.0
 */
@Slf4j
public final class PolicyInformationPointAttributeBroker implements AttributeBroker {

    private static final String ERROR_ANNOTATION_MISSING      = "PIP class %s is not annotated with @PolicyInformationPoint.";
    private static final String ERROR_BROKER_CLOSED           = "Cannot open a subscription: the attribute broker is closed.";
    private static final String ERROR_CATALOG_INVARIANT       = "Catalog invariant violated. Multiple exact matches for '{}'. Falling back to UNDEFINED or repository. The collision rule should have prevented this. Indicates an engine bug.";
    private static final String ERROR_DEPS_EMPTY              = "initialDependencies must not be empty";
    private static final String ERROR_GRACE_DURATION_NEGATIVE = "gracePeriodDuration must not be negative";
    private static final String ERROR_HANDLE_NOT_LOADED       = "Cannot swap PIP: handle for '%s' is not currently loaded.";
    private static final String ERROR_LOAD_PROCESSOR_FAILED   = "Failed to register PIP '%s': %s";
    private static final String ERROR_ONUPDATE_THREW          = "Subscription {} onUpdate callback threw (engine invariant: it must never throw). Ignoring this fire: {}";
    private static final String ERROR_REPOSITORY_CLOSE        = "Failed to close the repository observation for '{}'.";
    private static final String ERROR_RETURNED_DEPS_INVALID   = "Subscription {} onUpdate returned empty/null dependencies (engine invariant: it must return a non-empty set). Ignoring this fire and keeping the current dependency set.";
    private static final String ERROR_SPEC_COLLISION          = "Cannot register PIP '%s': attribute '%s' (parameter shape %s) collides with already-registered PIP '%s'.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK   = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE  = "subscriptionId already open: %s";

    private static final long LOG_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final ReentrantLock                                                lock                          = new ReentrantLock(
            true);
    private final Map<PipHandleImpl, List<StreamAttributeFinderSpecification>> handleSpecs                   = new LinkedHashMap<>();
    private final Map<AttributeFinderInvocation, List<ActiveInvocation>>       activeInvocationsByInvocation = new HashMap<>();
    private final Map<ActiveInvocation, ScheduledFuture<?>>                    pendingTeardowns              = new HashMap<>();
    private final Map<String, BrokerSubscription>                              consumerSubscriptions         = new HashMap<>();

    private final Map<RepositoryRoute, RepositoryObservation> repositoryObservations = new HashMap<>();

    private final Duration                      gracePeriodDuration;
    private final ScheduledExecutorService      teardownScheduler;
    private final @Nullable AttributeRepository fallback;
    private final InstantSource                 timestampSource;

    // Set in close(). Guards open() and teardown scheduling.
    private volatile boolean closed = false;

    /** No grace period, no fallback. */
    public PolicyInformationPointAttributeBroker() {
        this(Duration.ZERO, null);
    }

    /**
     * Constructs a broker with the given grace period and no fallback.
     * Refcount-to-zero leads to teardown after the
     * grace duration, or immediately if the duration is {@link Duration#ZERO}.
     *
     * @param gracePeriodDuration
     * teardown delay after refcount-zero; {@link Duration#ZERO} disables grace
     */
    public PolicyInformationPointAttributeBroker(@NonNull Duration gracePeriodDuration) {
        this(gracePeriodDuration, null);
    }

    /**
     * Constructs a broker with a fallback. Invocations that have no matching PIP
     * are served by {@code fallback}
     * (typically an
     * {@link io.sapl.attributes.broker.repository.InMemoryAttributeRepository}).
     * PIP-served invocations
     * are served exclusively by the PIP. Runtime UNDEFINED from a PIP is propagated
     * as UNDEFINED, never silently
     * replaced by the fallback's value. Routing is decided once when an active
     * invocation is created and re-evaluated
     * only on catalog mutations ({@link #load}, {@link #swap},
     * {@link PipHandle#unload}).
     *
     * @param gracePeriodDuration
     * teardown delay after refcount-zero; {@link Duration#ZERO} disables grace
     * @param fallback
     * repository that serves invocations with no matching PIP; {@code null} means
     * consumers see UNDEFINED
     * for unmatched invocations
     */
    public PolicyInformationPointAttributeBroker(@NonNull Duration gracePeriodDuration,
            @Nullable AttributeRepository fallback) {
        this(gracePeriodDuration, fallback, InstantSource.system());
    }

    /**
     * As
     * {@link #PolicyInformationPointAttributeBroker(Duration, AttributeRepository)},
     * with an explicit source for
     * attribute value-arrival timestamps. The source is read whenever a value
     * enters an active invocation's mailbox, so
     * the snapshot timestamp reflects arrival time, not read time.
     *
     * @param gracePeriodDuration
     * teardown delay after refcount-zero; {@link Duration#ZERO} disables grace
     * @param fallback
     * repository that serves invocations with no matching PIP; {@code null} means
     * consumers see UNDEFINED
     * for unmatched invocations
     * @param timestampSource
     * source for value-arrival timestamps
     */
    public PolicyInformationPointAttributeBroker(@NonNull Duration gracePeriodDuration,
            @Nullable AttributeRepository fallback,
            @NonNull InstantSource timestampSource) {
        if (gracePeriodDuration.isNegative()) {
            throw new IllegalArgumentException(ERROR_GRACE_DURATION_NEGATIVE);
        }
        this.gracePeriodDuration = gracePeriodDuration;
        this.fallback            = fallback;
        this.timestampSource     = timestampSource;
        this.teardownScheduler   = Executors.newSingleThreadScheduledExecutor(runnable -> {
                                     val thread = Thread.ofVirtual().unstarted(runnable);
                                     thread.setName("PolicyInformationPointAttributeBroker-teardown");
                                     return thread;
                                 });
    }

    /**
     * Loads the given PIP instance and registers all of its annotated attribute
     * methods. Atomic: either every spec the
     * instance contributes is registered and a handle is returned, or the call
     * throws {@link PipLoadException} and the
     * catalog is unchanged.
     *
     * @param pipInstance
     * a fully-constructed PIP instance whose class is annotated with
     * {@link PolicyInformationPoint}
     *
     * @return a handle the caller may use to unload or swap this PIP
     *
     * @throws PipLoadException
     * if the class is not annotated, if any annotated method has an invalid
     * signature, or if any of the
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
            promotions.putAll(collectPromotions());
            log.debug("Loaded PIP '{}' with {} attribute(s)", namespace, newSpecs.size());
        } finally {

            lock.unlock();

        }
        applyPromotions(promotions);
        return handle;
    }

    /**
     * Collects fallback-backed or terminal active invocations that now resolve to
     * a registered spec, so they can be promoted to PIP-backed. Must be called
     * holding {@link #lock} after the new specs are installed; the migrations
     * themselves run outside the lock via {@link #applyPromotions}.
     */
    private Map<ActiveInvocation, StreamAttributeFinderSpecification> collectPromotions() {
        val promotions = new LinkedHashMap<ActiveInvocation, StreamAttributeFinderSpecification>();
        for (val active : allActive()) {
            if (active.sourceSpec() != null) {
                continue;
            }
            resolve(active.invocation()).ifPresent(spec -> promotions.put(active, spec));
        }
        return promotions;
    }

    /**
     * Migrates the collected promotions to PIP-backed invocations. Call outside
     * {@link #lock}; {@link #migrate} preserves the prior value until the new
     * source emits, so a promotion is invisible to consumers.
     */
    private void applyPromotions(Map<ActiveInvocation, StreamAttributeFinderSpecification> promotions) {
        for (val entry : promotions.entrySet()) {
            migrate(entry.getKey(), newActivePipInvocation(entry.getKey().invocation(), entry.getValue()));
        }
    }

    /**
     * Atomically replaces the specs registered by {@code oldHandle} with the specs
     * contributed by {@code newInstance}.
     * Active invocations whose source resolved through {@code oldHandle} are
     * rebound to the matching new spec without
     * publishing a transient value to their consumers. Specs that exist in
     * {@code oldHandle} but have no shape match in
     * {@code newInstance} are evicted. Their active invocations migrate to the
     * fallback when one is configured,
     * otherwise they publish {@link Value#UNDEFINED} and close. Specs new in
     * {@code newInstance} promote any existing fallback-backed or terminal
     * active invocation that now resolves to them, exactly as {@link #load}
     * does, as well as serving future invocations.
     * <p>
     * Atomic: either the swap completes or the call throws and the catalog still
     * reflects {@code oldHandle}.
     *
     * @param oldHandle
     * the handle to replace (must currently be loaded)
     * @param newInstance
     * the replacement PIP instance
     *
     * @return a fresh handle for {@code newInstance}; {@code oldHandle} is marked
     * unloaded
     *
     * @throws PipLoadException
     * on the same conditions as {@link #load}, plus when {@code oldHandle} is not
     * currently loaded
     */
    public PipHandle swap(@NonNull PipHandle oldHandle, @NonNull Object newInstance) {
        if (!(oldHandle instanceof PipHandleImpl existingHandle) || existingHandle.broker() != this) {
            throw new PipLoadException(ERROR_HANDLE_NOT_LOADED.formatted(oldHandle.pipName()));
        }
        val newNamespace = readNamespace(newInstance);
        log.debug("Swapping PIP '{}' -> '{}' (new class {})", existingHandle.pipName(), newNamespace,
                newInstance.getClass().getName());

        val newSpecs         = extractSpecs(newInstance, newNamespace);
        val newDocumentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(newInstance.getClass());

        SwapPlan                                                  plan;
        Affected                                                  affected;
        PipHandleImpl                                             newHandle;
        Map<ActiveInvocation, StreamAttributeFinderSpecification> promotions;

        lock.lock();

        try {
            val oldSpecs = handleSpecs.get(existingHandle);
            if (oldSpecs == null || !existingHandle.loaded.get()) {
                throw new PipLoadException(ERROR_HANDLE_NOT_LOADED.formatted(existingHandle.pipName()));
            }
            checkCollisions(newNamespace, newSpecs, oldSpecs);

            plan = buildSwapPlan(oldSpecs, newSpecs);

            handleSpecs.remove(existingHandle);
            existingHandle.loaded.set(false);
            newHandle = new PipHandleImpl(newNamespace, newDocumentation);
            handleSpecs.put(newHandle, List.copyOf(newSpecs));

            affected = collectAffected(plan);
            // New specs are installed. Promote any fallback/terminal invocation that now
            // resolves to one of them, mirroring load(). Migration runs outside the lock.
            promotions = collectPromotions();
        } finally {

            lock.unlock();

        }

        // Outside the lock: rebind / evict. invoke(...) on the new spec may throw or
        // block.
        val fallbackRepository = fallback;
        for (val entry : affected.swapped.entrySet()) {
            rebind(entry.getKey(), entry.getValue());
        }
        for (val active : affected.evicted) {
            if (fallbackRepository != null) {
                migrate(active, new ActiveRepositoryInvocation(active.invocation(), this));
            } else {
                discard(active);
            }
        }
        applyPromotions(promotions);

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
     * Lists the currently loaded PIP handles.
     *
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
     * Returns documentation for the currently loaded PIPs.
     *
     * @return the {@link LibraryDocumentation} for every PIP currently in the
     * catalog, suitable for IDE
     * hover/completion and offline documentation generation. Returned in load
     * order. Reflects the catalog at
     * the moment of the call.
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
     * Resolves an invocation to its serving spec. By the catalog's collision rule,
     * at most one spec matches. Visible
     * mainly for tests. Consumers normally exercise resolution implicitly via
     * {@link #open}.
     *
     * @param invocation
     * the policy-driven invocation
     *
     * @return the matching spec, or {@link Optional#empty()} if no loaded PIP
     * serves this invocation
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

        BrokerSubscription subscription = null;
        boolean            fireImmediately;
        lock.lock();

        try {
            if (closed) {
                throw new IllegalStateException(ERROR_BROKER_CLOSED);
            }
            if (consumerSubscriptions.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            subscription = new BrokerSubscription(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            for (val key : initialDependencies) {
                // Get the active invocation for this key, reusing an existing one if possible.
                val activeInvocation = activeInvocationFor(key);
                // Record the route so dispatch can find the active invocation for this key.
                subscription.routingTable.put(key, activeInvocation);
                // Attach this subscription to the active invocation for refcount bookkeeping.
                activeInvocation.attach(subscription);
            }
            // Register the subscription in the broker's consumer index.
            consumerSubscriptions.put(subscriptionId, subscription);
            fireImmediately = subscription.isReadyForRound();
        } catch (RuntimeException e) {
            // Roll back dependencies attached so far. The subscription was never
            // registered.
            if (subscription != null) {
                subscription.close();
            }
            throw e;
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
        Collection<ActiveInvocation> activeToClose;
        lock.lock();

        try {
            closed = true;
            // Seal subscriptions under the activation lock before releasing any sources.
            for (val subscription : consumerSubscriptions.values()) {
                subscription.markClosed();
            }
            activeToClose = new ArrayList<>(allActive());
            consumerSubscriptions.clear();
            activeInvocationsByInvocation.clear();
            for (val teardownTask : pendingTeardowns.values()) {
                teardownTask.cancel(false);
            }
            pendingTeardowns.clear();
        } finally {

            lock.unlock();

        }
        for (val active : activeToClose) {
            active.close();
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
     * {@code fresh=true}: always creates a new active invocation and appends it to
     * the per-invocation list.
     * {@code fresh=false}: attaches to the first live entry in that list,
     * cancelling any pending teardown on it (warm
     * reconnect during the grace window).
     * <p>
     * The list is keyed by the normalized invocation, which drops the {@code fresh}
     * flag. Fresh and non-fresh subscriptions for an otherwise-identical
     * invocation land in the same list. Fresh requests bypass reuse for their
     * own attachment; later non-fresh requests may reuse any live entry,
     * including one created for a still-attached fresh consumer.
     */
    private ActiveInvocation activeInvocationFor(SubscriptionKey key) {
        val invocation = key.invocation();
        // Normalize to ignore the "fresh" flag for deduping. Ugly but functional.
        val normalizedInvocation = normalizeInvocation(invocation);
        val activeInvocations    = activeInvocationsByInvocation.computeIfAbsent(normalizedInvocation,
                key2 -> new ArrayList<>());
        if (!invocation.fresh()) {
            // Attempt to reuse an existing active invocation.
            for (val candidate : activeInvocations) {
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
        activeInvocations.add(active);
        return active;
    }

    /**
     * Returns the invocation with its {@code fresh} flag zeroed. Used as the
     * dedup-map key so consumers with different
     * {@code fresh} values for the same underlying attribute share a per-invocation
     * list.
     */
    private static AttributeFinderInvocation normalizeInvocation(AttributeFinderInvocation invocation) {
        if (!invocation.fresh()) {
            return invocation;
        }
        return new AttributeFinderInvocation(invocation.pdpId(), invocation.configurationId(),
                invocation.attributeName(), invocation.entity(), invocation.arguments(), invocation.initialTimeOut(),
                invocation.pollInterval(), invocation.backoff(), invocation.retries(), false, invocation.ctx());
    }

    /**
     * Caller holds the broker lock.
     * <p>
     * Routes by catalog state at activation time. A PIP match yields an
     * {@link ActivePolicyInformationPointInvocation}
     * fed by an {@link AttributeStream} (poll / retry / timeout cycle around the
     * PIP). No PIP match and a configured
     * fallback yields an {@link ActiveRepositoryInvocation} observing the fallback.
     * No PIP match and no fallback yields
     * a terminal active invocation preloaded with {@link Value#UNDEFINED}.
     * <p>
     * The PIP receives the normalized invocation (with {@code fresh} zeroed).
     * {@code fresh} is a broker-internal flag
     * and never leaves this layer.
     */
    private ActiveInvocation activateInvocationFor(AttributeFinderInvocation invocation) {
        val maybeAttributeFinderSpec = resolve(invocation);
        if (maybeAttributeFinderSpec.isPresent()) {
            val activeInvocation = newActivePipInvocation(invocation, maybeAttributeFinderSpec.get());
            activeInvocation.start();
            return activeInvocation;
        }
        val fallbackRepository = fallback;
        if (fallbackRepository != null) {
            val active = new ActiveRepositoryInvocation(invocation, this);
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
        holder[0] = new ActivePolicyInformationPointInvocation(normalized, stream, spec, timestampSource,
                value -> dispatchValue(holder[0]));
        return holder[0];
    }

    /**
     * Wraps {@code supplier} so the first call returns a stream opened
     * synchronously on the current (broker) thread,
     * and every subsequent call delegates to {@code supplier} directly. The
     * synchronous first open lets PIPs register
     * subscribers, open connections, and take other setup side effects before this
     * method returns, so values emitted on
     * the PIP side land on a registered subscriber rather than being dropped.
     * <p>
     * If the synchronous first open throws (transient connect-time failure), the
     * wrapper stores no cached first. The
     * AttributeStream is still constructed, and the pump's first cycle re-invokes
     * the supplier where the exception
     * propagates into the retry burst. Without this, broker.open / load / swap
     * would surface the transient failure on
     * the caller's thread instead of recovering under backoff.
     * <p>
     * The wrapper is {@link AutoCloseable} so the consumer (the AttributeStream)
     * can release the synchronously-opened
     * stream if it is closed before the pump consumed it. Without this, fast
     * activate-then-deactivate churn leaks PIP
     * streams nobody drains.
     */
    private static Supplier<Stream<Value>> openSyncFirstThenLazy(Supplier<Stream<Value>> supplier) {
        return new SyncFirstThenLazySupplier(supplier);
    }

    private static final class SyncFirstThenLazySupplier implements Supplier<Stream<Value>>, AutoCloseable {

        private final Supplier<Stream<Value>>        delegate;
        private final AtomicReference<Stream<Value>> first;

        SyncFirstThenLazySupplier(Supplier<Stream<Value>> delegate) {
            this.delegate = delegate;
            Stream<Value> opened;
            try {
                opened = delegate.get();
            } catch (RuntimeException e) {
                log.debug("Synchronous first open threw. The pump will retry: {}", e.getMessage());
                opened = null;
            }
            this.first = new AtomicReference<>(opened);
        }

        @Override
        public Stream<Value> get() {
            val preopenedStream = first.getAndSet(null);
            return preopenedStream != null ? preopenedStream : delegate.get();
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

    private record RepositoryRoute(String pdpId, String configurationId) {
        RepositoryRoute(AttributeFinderInvocation invocation) {
            this(invocation.pdpId(), invocation.configurationId());
        }
    }

    private static final class RepositoryObservation {
        private final Set<ActiveRepositoryInvocation>           active = new HashSet<>();
        private AttributeRepository.@Nullable StateRegistration registration;
        private @Nullable RepositoryState                       latest;
    }

    /**
     * Attaches a key to its route's shared source observation. Caller may already
     * hold the routing lock. Register before consuming the fixed initial view:
     * a successor callback can arrive first and must not be overwritten by an
     * older initial revision. The key is then projected from the newest accepted
     * view, without a separate source read that could cross a commit boundary.
     */
    void startRepositoryInvocation(ActiveRepositoryInvocation active) {
        boolean changed;
        lock.lock();
        try {
            if (closed || active.isClosed() || fallback == null) {
                return;
            }
            val route       = new RepositoryRoute(active.invocation());
            var observation = repositoryObservations.get(route);
            if (observation == null) {
                val opened = new RepositoryObservation();
                repositoryObservations.put(route, opened);
                try {
                    opened.registration = fallback.observeState(route.pdpId(), route.configurationId(),
                            state -> installRepositoryState(route, opened, state));
                    val initial = opened.registration.initialState();
                    if (opened.latest == null || opened.latest.revision() < initial.revision()) {
                        opened.latest = initial;
                    }
                } catch (RuntimeException e) {
                    repositoryObservations.remove(route);
                    if (opened.registration != null) {
                        opened.registration.close();
                    }
                    throw e;
                }
                observation = opened;
            }
            observation.active.add(active);
            changed = active.install(observation.latest, timestampSource.instant());
        } finally {
            lock.unlock();
        }
        if (changed) {
            dispatchValue(active);
        }
    }

    /**
     * Installs one complete route view under the same lock as round capture.
     * Check both registration identity and revision: numbering may restart for
     * a replacement observation, and an old callback may survive detachment.
     * Update every active projection before testing gates or requesting wakes.
     * Full-view comparison also carries disjoint changes across skipped revisions.
     */
    private void installRepositoryState(RepositoryRoute route, RepositoryObservation observation,
            RepositoryState next) {
        val affected = new HashSet<BrokerSubscription>();
        lock.lock();
        try {
            if (closed || repositoryObservations.get(route) != observation
                    || observation.latest != null && next.revision() <= observation.latest.revision()) {
                return;
            }
            val observedAt = timestampSource.instant();
            observation.latest = next;
            for (val active : observation.active) {
                if (active.install(next, observedAt)) {
                    affected.addAll(active.subscribers());
                }
            }
            // Test readiness only after all aliases have moved to the same source revision.
            affected.removeIf(subscription -> !subscription.isReadyForRound());
        } finally {
            lock.unlock();
        }
        affected.forEach(BrokerSubscription::fireCallback);
    }

    /**
     * Retires the route's observation when its final active projection closes.
     * Remove its identity under the routing lock before detaching, so a callback
     * already in flight cannot revive the retired view. Other active repository
     * keys on the route keep their shared observation.
     */
    void stopRepositoryInvocation(ActiveRepositoryInvocation active) {
        AttributeRepository.StateRegistration toClose = null;
        lock.lock();
        try {
            val route       = new RepositoryRoute(active.invocation());
            val observation = repositoryObservations.get(route);
            if (observation != null && observation.active.remove(active) && observation.active.isEmpty()) {
                repositoryObservations.remove(route);
                toClose = observation.registration;
            }
        } finally {
            lock.unlock();
        }
        if (toClose != null) {
            try {
                toClose.close();
            } catch (RuntimeException failure) {
                log.error(ERROR_REPOSITORY_CLOSE, active.invocation().pdpId(), failure);
            }
        }
    }

    private ActivePolicyInformationPointInvocation newTerminalActiveInvocation(AttributeFinderInvocation normalized) {
        val holder = new ActivePolicyInformationPointInvocation[1];
        holder[0] = new ActivePolicyInformationPointInvocation(normalized, null, null, timestampSource,
                value -> dispatchValue(holder[0]));
        holder[0].publishImmediate(Value.UNDEFINED);
        return holder[0];
    }

    /**
     * Replaces {@code previous} with {@code replacement} in the per-invocation list
     * and in every subscribed consumer's
     * routing table. Subscriber refcount is preserved across the swap. Starts
     * {@code replacement} and closes
     * {@code previous} after the state move is committed.
     * <p>
     * Used by catalog mutations to re-route active invocations when their source
     * changes. {@link #load} promotes
     * repository-backed or terminal active invocations to PIP-backed ones.
     * {@link #swap} or {@link PipHandle#unload}
     * demotes PIP-backed active invocations whose spec is gone to repository-backed
     * or terminal.
     */
    private void migrate(ActiveInvocation previous, ActiveInvocation replacement) {
        lock.lock();

        try {
            if (closed) {
                // close() already drained and closed everything. Do not re-insert
                // or start the replacement. That would leak its pump and source.
                replacement.close();
                return;
            }
            val activeInvocations = activeInvocationsByInvocation.get(previous.invocation());
            if (activeInvocations != null) {
                val index = activeInvocations.indexOf(previous);
                if (index >= 0) {
                    activeInvocations.set(index, replacement);
                } else {
                    activeInvocations.add(replacement);
                }
            } else {
                activeInvocationsByInvocation.computeIfAbsent(previous.invocation(), key -> new ArrayList<>())
                        .add(replacement);
            }
            cancelPendingTeardown(previous);
            val subscribersSnapshot = new ArrayList<>(previous.subscribers());
            for (val consumer : subscribersSnapshot) {
                for (val entry : consumer.routingTable.entrySet()) {
                    if (entry.getValue() == previous) {
                        entry.setValue(replacement);
                        previous.detach(consumer);
                        replacement.attach(consumer);
                    }
                }
            }
        } finally {

            lock.unlock();

        }
        replacement.start();
        previous.close();
        reclaimIfOrphaned(replacement);
    }

    /**
     * Caller must not hold the broker lock. If {@code invocation} has no
     * subscribers, applies the same teardown policy
     * as a refcount-zero drop: schedules a grace-period teardown, or removes and
     * closes it when there is no grace
     * window. {@link #migrate} transfers the old invocation's subscribers to the
     * replacement, but when the old
     * invocation was in its refcount-zero grace window there are none to transfer,
     * so the replacement is promoted with
     * no subscribers. Because {@code handleRefcountZero} only fires on a consumer
     * detach, such a born-orphaned
     * replacement would never be reclaimed and its pump would run for the life of
     * the broker. This closes that gap
     * defensively.
     */
    private void reclaimIfOrphaned(ActiveInvocation invocation) {
        lock.lock();
        try {
            if (invocation.refcount() > 0) {
                return;
            }
            val activeInvocations = activeInvocationsByInvocation.get(invocation.invocation());
            val teardownNow       = (activeInvocations != null && activeInvocations.size() > 1)
                    || gracePeriodDuration.isZero();
            if (!teardownNow) {
                if (!pendingTeardowns.containsKey(invocation)) {
                    scheduleTeardown(invocation);
                }
                return;
            }
            removeFromList(invocation);
        } finally {
            lock.unlock();
        }
        invocation.close();
    }

    /**
     * Builds the inner-stream supplier that {@link AttributeStream} calls at the
     * start of each cycle. Lets invoke-time
     * {@link RuntimeException}s propagate so the retry/burst logic in
     * {@link AttributeStream#attemptWithRetries} treats
     * an open-time failure the same as a mid-stream failure (failed attempt,
     * jittered backoff, eventual
     * retries-exhausted ErrorValue). A transient connect-time error (MQTT broker
     * briefly down, HTTP 503 on send)
     * recovers on the same schedule as a transient mid-stream error.
     */
    private static Supplier<Stream<Value>> innerStreamSupplier(StreamAttributeFinderSpecification spec,
            AttributeFinderInvocation invocation) {
        return () -> spec.attributeFinder().invoke(invocation);
    }

    /**
     * Returns a snapshot of every live active invocation across all per-invocation
     * lists. Caller holds the broker lock.
     */
    private List<ActiveInvocation> allActive() {
        val everyActive = new ArrayList<ActiveInvocation>();
        for (val activeInvocations : activeInvocationsByInvocation.values()) {
            everyActive.addAll(activeInvocations);
        }
        return everyActive;
    }

    private void rebind(ActivePolicyInformationPointInvocation active, StreamAttributeFinderSpecification newSpec) {
        val           supplier  = openSyncFirstThenLazy(innerStreamSupplier(newSpec, active.invocation()));
        Stream<Value> newStream = new AttributeStream(active.invocation(), supplier);
        active.rebind(newStream, newSpec);
    }

    /**
     * Publishes UNDEFINED to the active invocation's mailbox, removes it from the
     * per-invocation list and any pending
     * teardown registration, and closes it. Used by catalog mutations that remove
     * the serving PIP when no fallback
     * exists.
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
     * Caller holds the broker lock. Removes the active invocation from its
     * invocation's list, dropping the list itself
     * when it becomes empty.
     */
    private void removeFromList(ActiveInvocation activeInvocation) {
        val activeInvocations = activeInvocationsByInvocation.get(activeInvocation.invocation());
        if (activeInvocations == null) {
            return;
        }
        activeInvocations.remove(activeInvocation);
        if (activeInvocations.isEmpty()) {
            activeInvocationsByInvocation.remove(activeInvocation.invocation());
        }
    }

    /**
     * Caller holds the broker lock. Cancels a scheduled grace-period teardown for
     * {@code activeInvocation}, if one is
     * pending.
     */
    private void cancelPendingTeardown(ActiveInvocation activeInvocation) {
        val teardownTask = pendingTeardowns.remove(activeInvocation);
        if (teardownTask != null) {
            teardownTask.cancel(false);
        }
    }

    /**
     * Caller holds the broker lock. Schedules the active invocation for teardown
     * after the configured grace duration.
     * The task is idempotent against cancellation via {@link #pendingTeardowns}.
     */
    private void scheduleTeardown(ActiveInvocation activeInvocation) {
        if (closed) {
            // close() already tears down everything and shut down the scheduler.
            return;
        }
        val teardownTask = teardownScheduler.schedule(() -> runScheduledTeardown(activeInvocation),
                gracePeriodDuration.toMillis(), TimeUnit.MILLISECONDS);
        pendingTeardowns.put(activeInvocation, teardownTask);
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
     * Called by an {@link ActiveInvocation} on each value publication. Iterates
     * only this active invocation's own
     * subscriber set (a reverse index maintained by attach/detach), so dispatch is
     * O(consumers-of-this-active-invocation), not O(all-consumers).
     */
    private void dispatchValue(ActiveInvocation active) {
        List<BrokerSubscription> toFire;
        lock.lock();

        try {
            toFire = new ArrayList<>();
            for (val consumer : active.subscribers()) {
                if (consumer.isReadyForRound()) {
                    toFire.add(consumer);
                }
            }
        } finally {

            lock.unlock();

        }
        for (val subscription : toFire) {
            subscription.fireCallback();
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
        val fallbackRepository = fallback;
        for (val active : evictedActive) {
            if (fallbackRepository != null) {
                migrate(active, new ActiveRepositoryInvocation(active.invocation(), this));
            } else {
                discard(active);
            }
        }
        log.debug("Unloaded PIP '{}' ({} attribute(s) removed)", handle.pipName(), evicted.size());
    }

    /**
     * Subscription handle for one consumer. Owns the per-consumer routing table,
     * the previously delivered inputs, and the callback.
     * Serializes onUpdate firing through a {@link DispatchCoalescer}: rapid
     * publishes during an in-flight
     * {@code onUpdate} collapse into one re-fire afterwards, against the latest
     * mailbox snapshot.
     */
    final class BrokerSubscription implements Subscription {

        final String                                                                  id;
        final DispatchCoalescer                                                       coalescer;
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          dependencies;
        Map<SubscriptionKey, ActiveInvocation>                                        routingTable       = new HashMap<>();
        boolean                                                                       subscriptionClosed = false;

        private final Map<SubscriptionKey, AttributeSnapshot> deliveredInputs = new HashMap<>();

        // Rate-limits the should-never-happen onUpdate contract-violation error logs to
        // one per minute.
        private long    lastErrorLogNanos;
        private boolean errorLogged;

        BrokerSubscription(String id,
                Set<SubscriptionKey> dependencies,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id           = id;
            this.dependencies = dependencies;
            this.onUpdate     = onUpdate;
            this.coalescer    = new DispatchCoalescer(this::runOneFire);
        }

        @Override
        public void close() {
            List<ActiveInvocation> refcountZeroed;
            lock.lock();

            try {
                if (subscriptionClosed) {
                    return;
                }
                subscriptionClosed = true;
                consumerSubscriptions.remove(id);
                refcountZeroed = new ArrayList<>();
                for (val active : routingTable.values()) {
                    if (active.detach(this) <= 0) {
                        refcountZeroed.add(active);
                    }
                }
                routingTable.clear();
                deliveredInputs.clear();
            } finally {

                lock.unlock();

            }
            for (val active : refcountZeroed) {
                handleRefcountZero(active);
            }
            log.trace("Closed subscription '{}'", id);
        }

        void markClosed() {
            lock.lock();

            try {
                subscriptionClosed = true;
                deliveredInputs.clear();
            } finally {

                lock.unlock();

            }
        }

        /**
         * Caller holds the routing lock. First values wait for the entire current
         * set. A new observation of a previously delivered live key can start a
         * partial round. Snapshot identity distinguishes renewals even when the
         * value and clock are equal, and suppresses already captured notifications.
         */
        boolean isReadyForRound() {
            boolean allFulfilled  = true;
            boolean hasFirstValue = false;
            for (val key : dependencies) {
                val active  = routingTable.get(key);
                val current = active == null ? null : active.snapshot().orElse(null);
                if (current == null) {
                    allFulfilled = false;
                    continue;
                }
                val previous = deliveredInputs.get(key);
                if (previous == null) {
                    hasFirstValue = true;
                } else if (previous != current) {
                    return true;
                }
            }
            return allFulfilled && hasFirstValue;
        }

        /**
         * Captures this consumer's registered keys; caller holds the routing
         * lock. Repository projections move together under that lock, and the
         * immutable result stays fixed after the lock is released. This is a
         * round's input subset, not the entire repository view. A newly
         * discovered key enters a subsequent round through dependency handling.
         * PIPs can publish while capture holds the routing lock. Sample each
         * physical invocation once, including an empty mailbox, so all aliases
         * of that invocation see the same observation in this round.
         */
        Map<SubscriptionKey, AttributeSnapshot> currentSnapshots() {
            val snapshotsByKey = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(dependencies.size());
            val observations   = new IdentityHashMap<ActiveInvocation, Optional<AttributeSnapshot>>(
                    dependencies.size());
            for (val key : dependencies) {
                val active = routingTable.get(key);
                if (active == null) {
                    continue;
                }
                observations.computeIfAbsent(active, ActiveInvocation::snapshot)
                        .ifPresent(snapshot -> snapshotsByKey.put(key, snapshot));
            }
            return Map.copyOf(snapshotsByKey);
        }

        void fireCallback() {
            coalescer.requestFire();
        }

        /**
         * Runs one serialized round. Recheck closure and readiness under the
         * capture lock. Remember the actual inputs before invoking the callback,
         * so an update during evaluation survives discovery of missing keys.
         * Capture current inputs, release the lock, invoke the
         * evaluator, then reconcile its full dependency set. An in-flight round
         * keeps its captured inputs even if newer values arrive meanwhile.
         */
        private void runOneFire() {
            Map<SubscriptionKey, AttributeSnapshot>                                 snapshotsByKey;
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> updateCallback;
            lock.lock();

            try {
                if (subscriptionClosed || !isReadyForRound()) {
                    return;
                }
                snapshotsByKey = currentSnapshots();
                deliveredInputs.putAll(snapshotsByKey);
                updateCallback = onUpdate;
            } finally {

                lock.unlock();

            }
            Set<SubscriptionKey> nextDependencies;
            try {
                nextDependencies = updateCallback.apply(snapshotsByKey);
            } catch (RuntimeException e) {
                // Engine invariant: the callback must never throw. Defensive backstop -
                // log loudly and survive. Never propagate onto this shared dispatch thread.
                logRateLimited(() -> log.error(ERROR_ONUPDATE_THREW, id, e.getMessage(), e));
                return;
            }
            if (nextDependencies == null || nextDependencies.isEmpty()) {
                // Engine invariant: the callback must return a non-empty dep set. Defensive
                // backstop - log loudly, keep the current dep set, never throw onto this
                // thread.
                logRateLimited(() -> log.error(ERROR_RETURNED_DEPS_INVALID, id));
                return;
            }
            applyDependencyDiff(nextDependencies);
        }

        private void logRateLimited(Runnable logCall) {
            val now = System.nanoTime();
            if (!errorLogged || now - lastErrorLogNanos >= LOG_INTERVAL_NANOS) {
                logCall.run();
                lastErrorLogNanos = now;
                errorLogged       = true;
            }
        }

        /**
         * Reconciles the full set returned by this round, not a delta. New keys
         * attach to current sources. Recheck readiness after the set changes:
         * a complete first-value set or an update of a retained known key can
         * complete lazy discovery without another source notification. Removed keys release this
         * consumer's references and do not by themselves request another round.
         */
        private void applyDependencyDiff(Set<SubscriptionKey> nextDependencies) {
            List<ActiveInvocation> refcountZeroed = new ArrayList<>();
            boolean                refire;
            lock.lock();

            try {
                if (closed || subscriptionClosed) {
                    return;
                }
                if (nextDependencies.equals(dependencies)) {
                    return;
                }
                val added = new HashSet<>(nextDependencies);
                added.removeAll(dependencies);
                val removed = new HashSet<>(dependencies);
                removed.removeAll(nextDependencies);

                for (val key : added) {
                    val active = activeInvocationFor(key);
                    routingTable.put(key, active);
                    active.attach(this);
                }
                for (val key : removed) {
                    val active = routingTable.remove(key);
                    if (active != null && active.detach(this) <= 0) {
                        refcountZeroed.add(active);
                    }
                }
                dependencies = new HashSet<>(nextDependencies);
                deliveredInputs.keySet().removeAll(removed);
                refire = isReadyForRound();
            } finally {

                lock.unlock();

            }
            for (val active : refcountZeroed) {
                handleRefcountZero(active);
            }
            if (refire) {
                fireCallback();
            }
        }

        /**
         * Called when an active invocation's refcount drops to zero as a result of this
         * consumer detaching from it.
         * <p>
         * Three cases:
         * <ul>
         * <li>The per-invocation list has more than one entry: tear down immediately.
         * The other entries already serve
         * future {@code fresh=false} arrivals, so a grace window here would just delay
         * reclaiming the resource.</li>
         * <li>Grace duration is zero: tear down immediately.</li>
         * <li>Otherwise: schedule teardown after the grace duration. The active
         * invocation stays in the list during the
         * window. A re-attaching {@code fresh=false} consumer cancels the scheduled
         * teardown and reuses the warm
         * connection.</li>
         * </ul>
         */
        private void handleRefcountZero(ActiveInvocation activeInvocation) {
            lock.lock();
            try {
                if (activeInvocation.refcount() > 0) {
                    return;
                }
                val activeInvocations = activeInvocationsByInvocation.get(activeInvocation.invocation());
                val teardownNow       = (activeInvocations != null && activeInvocations.size() > 1)
                        || gracePeriodDuration.isZero();
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
    }

    /**
     * Internal handle implementation. Tied to its enclosing broker instance so that
     * {@code unload()} routes back to the
     * correct catalog and so that {@code swap()} can reject foreign handles.
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
