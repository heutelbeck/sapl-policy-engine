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
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.AttributeSubscriptionObserver;
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
 * Catalog selection and physical enrollment have distinct lifetimes. A catalog change
 * selects a source for every affected invocation under the routing lock. A prepared
 * source can take over only while its selection is still current. Until then the
 * previously admitted source serves the consumer. At same-shape PIP replacement
 * its last observation carries forward until the replacement supplies a value.
 * A consumer of an unloaded PIP retains its interest across fallback, including
 * the terminal UNDEFINED value when no repository is configured.
 * <p>
 * An open reserves all logical interests before calling a finder or enrolling at a repository.
 * Compatible openers share one physical enrollment. A normal open acknowledges enrollment of
 * its initial keys before returning. A lazy round reserves newly reached keys and can finish
 * while their finders are opening, so an update of a known input can withdraw that interest.
 * Source code can open independent observations. A synchronous nested wait for an already
 * unfinished enrollment is rejected because its owner can depend on the calling source.
 * Broker-mediated wakes on a source stack are delivered by an independent, coalescing worker.
 * <p>
 * Repository values use one observation per active tenant/configuration route.
 * A callback supplies a complete {@link RepositoryState}. The broker rejects
 * callbacks from retired observations and revisions no newer than the installed
 * view. Under its routing lock it installs every affected active projection,
 * including different timing or fresh variants of the same repository key.
 * Capturing a consumer's input map uses that same lock. Thus no evaluation can
 * capture only half of a related repository update. Notifications are requested
 * after installation, outside the lock; the eventual round may use an even
 * newer view if updates coalesce. An expiry worker requests these rounds on
 * each subscription's coalescer asynchronously so it can admit later expiries.
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
    private static final String ERROR_AMBIGUOUS_ATTRIBUTE     = "Cannot register PIP '%s': multiple declarations match attribute '%s' (parameter shape %s).";
    private static final String ERROR_SPEC_COLLISION          = "Cannot register PIP '%s': attribute '%s' (parameter shape %s) collides with already-registered PIP '%s'.";
    private static final String ERROR_SOURCE_WAIT             = "A source cannot wait for another unfinished attribute registration.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK   = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE  = "subscriptionId already open: %s";

    private static final long LOG_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final ReentrantLock                                                lock                          = new ReentrantLock(
            true);
    private final Map<PipHandleImpl, List<StreamAttributeFinderSpecification>> handleSpecs                   = new LinkedHashMap<>();
    private final Map<AttributeFinderInvocation, List<ActiveInvocation>>       activeInvocationsByInvocation = new HashMap<>();
    // The current attempt, even when two successive changes choose the same provider.
    private final Map<ActiveInvocation, SourceChange>  changingSources       = new HashMap<>();
    private final Map<ActiveInvocation, GraceInterval> pendingTeardowns      = new HashMap<>();
    private final Map<String, BrokerSubscription>      consumerSubscriptions = new HashMap<>();

    // Private completions have only blocking waiters. No user continuation can run under the routing lock.
    private final Map<ActiveInvocation, Enrollment> openings = new IdentityHashMap<>();

    private record RoutingChanges(
            List<ActiveInvocation> retired,
            Map<ActiveInvocation, Enrollment> openings,
            boolean ready) {}

    private static final class Enrollment {
        final CompletableFuture<@Nullable RuntimeException> ready = new CompletableFuture<>();
        boolean                                             claimed;
    }

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
        val scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            val thread = Thread.ofVirtual().unstarted(runnable);
            thread.setName("PolicyInformationPointAttributeBroker-teardown");
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        this.teardownScheduler = scheduler;
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
     * name or signature, or if two finder declarations can match the same
     * name, receiver and argument shape with equal priority
     */
    public PipHandle load(@NonNull Object pipInstance) {
        val namespace = readNamespace(pipInstance);
        log.debug("Loading PIP '{}' from class {}", namespace, pipInstance.getClass().getName());

        val newSpecs      = extractSpecs(pipInstance, namespace);
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(pipInstance.getClass());

        PipHandleImpl      handle;
        List<SourceChange> promotions;
        lock.lock();

        try {
            checkCollisions(namespace, newSpecs, null);
            handle = new PipHandleImpl(namespace, documentation);
            handleSpecs.put(handle, List.copyOf(newSpecs));
            promotions = selectChanges(Map.of(), List.of());
            log.debug("Loaded PIP '{}' with {} attribute(s)", namespace, newSpecs.size());
        } finally {

            lock.unlock();

        }
        promotions.forEach(this::applyChange);
        return handle;
    }

    private record SourceChange(ActiveInvocation previous, @Nullable StreamAttributeFinderSpecification desired) {}

    /**
     * Caller holds the routing lock. A later change follows the desired catalog entry even
     * while its predecessor is still opening. Unrelated loads preserve an active PIP selection.
     */
    private List<SourceChange> selectChanges(
            Map<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification> swapped,
            List<StreamAttributeFinderSpecification> evicted) {
        val changes = new ArrayList<SourceChange>();
        for (val active : allActive()) {
            val prior    = changingSources.get(active);
            val selected = prior == null ? active.sourceSpec() : prior.desired();
            val desired  = desiredSource(active, selected, swapped, evicted);
            if (desired == selected) {
                continue;
            }
            if (desired == active.sourceSpec() && !openings.containsKey(active)) {
                changingSources.remove(active);
            } else {
                val change = new SourceChange(active, desired);
                changingSources.put(active, change);
                changes.add(change);
            }
        }
        return changes;
    }

    private @Nullable StreamAttributeFinderSpecification desiredSource(ActiveInvocation active,
            @Nullable StreamAttributeFinderSpecification selected,
            Map<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification> swapped,
            List<StreamAttributeFinderSpecification> evicted) {
        if (selected == null) {
            return resolve(active.invocation()).orElse(null);
        }
        val replacement = swapped.get(selected);
        if (replacement != null || evicted.contains(selected)) {
            return replacement;
        }
        return selected;
    }

    private void applyChange(SourceChange change) {
        val              previous    = change.previous();
        val              invocation  = previous.invocation();
        ActiveInvocation replacement = null;
        boolean          admitted;
        try {
            if (change.desired() != null) {
                replacement = newActivePipInvocation(invocation, change.desired(), previous.sourceSpec() != null);
            } else if (fallback != null) {
                replacement = new ActiveRepositoryInvocation(invocation, this);
                replacement.start();
            } else {
                replacement = newTerminalActiveInvocation(invocation);
            }
            admitted = replaceRoutes(previous, replacement, change);
        } catch (RuntimeException failure) {
            if (replacement != null) {
                release(replacement);
            }
            failAdmission(previous, null, change, failure);
            return;
        }
        if (!admitted) {
            release(replacement);
            return;
        }
        if (replacement instanceof ActivePolicyInformationPointInvocation) {
            replacement.start();
        }
        release(previous);
        dispatchValue(replacement);
        reclaimIfOrphaned(replacement);
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
     * otherwise their consumers observe {@link Value#UNDEFINED} until a matching PIP is loaded. Specs new in
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

        SwapPlan           plan;
        PipHandleImpl      newHandle;
        List<SourceChange> changes;
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

            changes = selectChanges(plan.swapped, plan.evictedOnly);
        } finally {

            lock.unlock();

        }

        changes.forEach(this::applyChange);

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

    private record SwapPlan(
            Map<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification> swapped,
            List<StreamAttributeFinderSpecification> evictedOnly,
            List<StreamAttributeFinderSpecification> addedOnly) {}

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
        val keys = new LinkedHashSet<>(initialDependencies);
        if (keys.isEmpty()) {
            throw new IllegalArgumentException(ERROR_DEPS_EMPTY);
        }

        val subscription = new BrokerSubscription(subscriptionId, keys, onUpdate);
        try {
            subscription.reconcile(keys, true);
            for (val key : keys) {
                subscription.awaitEnrollment(key);
            }
            lock.lock();
            try {
                if (subscription.pendingFailure != null) {
                    throw subscription.pendingFailure;
                }
                subscription.enrolled = true;
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException failure) {
            subscription.close();
            throw failure;
        }
        subscription.fireCallback();
        return subscription;
    }

    @Override
    public void close() {
        Collection<ActiveInvocation> activeToClose;
        val                          registrations = new HashMap<RepositoryRoute, AttributeRepository.StateRegistration>();
        lock.lock();

        try {
            closed        = true;
            activeToClose = new ArrayList<>(allActive());
            // Seal subscriptions under the activation lock before releasing any sources.
            for (val subscription : List.copyOf(consumerSubscriptions.values())) {
                subscription.detachUnderLock();
            }
            // A route preparing a catalog handoff can have a lease before its backing is in the sharing index.
            repositoryObservations.forEach((route, observation) -> {
                if (observation.registration != null) {
                    registrations.put(route, observation.registration);
                }
                observation.opened.complete(null);
                observation.active.clear();
            });
            repositoryObservations.clear();
            openings.values().forEach(opening -> opening.ready.complete(null));
            openings.clear();
            changingSources.clear();
            consumerSubscriptions.clear();
            activeInvocationsByInvocation.clear();
            for (val teardownTask : pendingTeardowns.values()) {
                teardownTask.future.cancel(false);
            }
            pendingTeardowns.clear();
        } finally {

            lock.unlock();

        }
        registrations.forEach(this::closeRegistration);
        for (val active : activeToClose) {
            release(active);
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
            StreamAttributeFinderSpecification spec;
            try {
                spec = StreamAttributeMethodSignatureProcessor.processAttributeMethod(instance, namespace, method);
            } catch (RuntimeException e) {
                val attrFqn = namespace + '.' + method.getName();
                log.debug("Failed to process attribute method '{}': {}", attrFqn, e.getMessage());
                throw new PipLoadException(ERROR_LOAD_PROCESSOR_FAILED.formatted(attrFqn, e.getMessage()), e);
            }
            if (spec == null) {
                continue;
            }
            if (specs.stream().anyMatch(spec::collidesWith)) {
                throw new PipLoadException(
                        ERROR_AMBIGUOUS_ATTRIBUTE.formatted(namespace, spec.fullyQualifiedName(), shapeOf(spec)));
            }
            specs.add(spec);
            log.trace("Processed attribute '{}' ({} fixed parameter(s){})", spec.fullyQualifiedName(),
                    spec.parameterTypes().size(), spec.hasVariableNumberOfArguments() ? " plus varargs" : "");
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
    private ActiveInvocation activeInvocationFor(SubscriptionKey key, @Nullable Instant observedAt) {
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
        val              spec = resolve(normalizedInvocation).orElse(null);
        ActiveInvocation active;
        if (spec == null && fallback != null) {
            active = new ActiveRepositoryInvocation(normalizedInvocation, this);
        } else {
            active = unstartedPip(normalizedInvocation, spec);
        }
        activeInvocations.add(active);
        if (!(active instanceof ActiveRepositoryInvocation repo && observedAt != null
                && projectCurrentView(repo, observedAt))) {
            openings.put(active, new Enrollment());
        }
        return active;
    }

    /** A provisional cold source cannot contribute data until its enrollment wins admission. */
    private Optional<AttributeSnapshot> admittedSnapshot(ActiveInvocation active) {
        return openings.containsKey(active) ? Optional.empty() : active.snapshot();
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

    private Instant observationTime() {
        return Objects.requireNonNull(AttributeSourceCall.invoke(timestampSource::instant));
    }

    private ActivePolicyInformationPointInvocation unstartedPip(AttributeFinderInvocation invocation,
            @Nullable StreamAttributeFinderSpecification spec) {
        val holder = new ActivePolicyInformationPointInvocation[1];
        holder[0] = new ActivePolicyInformationPointInvocation(invocation, null, spec, this::observationTime,
                value -> dispatchValue(holder[0]), lock, failure -> failAdmission(holder[0], null, null, failure));
        return holder[0];
    }

    private static void release(ActiveInvocation active) {
        AttributeSourceCall.invoke(() -> {
            active.close();
            return null;
        });
    }

    private static void awaitEnrollment(CompletableFuture<@Nullable RuntimeException> ready) {
        if (AttributeSourceCall.active() && !ready.isDone()) {
            throw new IllegalStateException(ERROR_SOURCE_WAIT);
        }
        val failure = ready.join();
        if (failure != null) {
            throw failure;
        }
    }

    /** Initial callers acknowledge enrollment. A lazy reconciliation only reserves a worker. */
    private void enroll(ActiveInvocation active, Enrollment opening, boolean wait) {
        boolean claim;
        lock.lock();
        try {
            if (openings.get(active) != opening) {
                return;
            }
            claim           = !opening.claimed;
            opening.claimed = true;
        } finally {
            lock.unlock();
        }
        if (claim) {
            if (wait && AttributeSourceCall.active()) {
                // An independent, unclaimed nested source can be prepared on this stack.
                finishEnrollment(active, opening);
            } else {
                Thread.startVirtualThread(() -> finishEnrollment(active, opening));
            }
        }
        if (wait) {
            awaitEnrollment(opening.ready);
        }
    }

    private void finishEnrollment(ActiveInvocation active, Enrollment opening) {
        try {
            prepare(active, opening);
            lock.lock();
            try {
                if (!changingSources.containsKey(active) && openings.remove(active, opening)) {
                    opening.ready.complete(null);
                }
            } finally {
                lock.unlock();
            }
            dispatchValue(active);
        } catch (RuntimeException failure) {
            failAdmission(active, opening, null, failure);
        }
    }

    private void prepare(ActiveInvocation active, Enrollment opening) {
        if (!(active instanceof ActivePolicyInformationPointInvocation pip)) {
            active.start();
            return;
        }
        val spec = pip.sourceSpec();
        if (spec == null) {
            pip.publishImmediate(Value.UNDEFINED);
            return;
        }
        val     invocation = active.invocation();
        val     prepared   = AttributeSourceCall.invoke(() -> new AttributeStream(invocation,
                openSyncFirstThenLazy(innerStreamSupplier(spec, invocation)), false));
        boolean accepted;
        lock.lock();
        try {
            accepted = openings.get(active) == opening && !changingSources.containsKey(active) && !closed;
            if (accepted) {
                pip.installPrepared(prepared);
            }
        } finally {
            lock.unlock();
        }
        if (accepted) {
            pip.start();
        } else {
            prepared.close();
        }
    }

    private ActivePolicyInformationPointInvocation newActivePipInvocation(AttributeFinderInvocation invocation,
            StreamAttributeFinderSpecification spec, boolean replacing) {
        val active = unstartedPip(invocation, spec);
        active.installPrepared(AttributeSourceCall.invoke(() -> new AttributeStream(invocation,
                openSyncFirstThenLazy(innerStreamSupplier(spec, invocation)), replacing)));
        return active;
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
                    AttributeSourceCall.close(unclaimed);
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
        final Set<ActiveRepositoryInvocation>               active = new HashSet<>();
        final CompletableFuture<@Nullable RuntimeException> opened = new CompletableFuture<>();
        AttributeRepository.@Nullable StateRegistration     registration;
        @Nullable
        RepositoryState                                     latest;
        @Nullable
        Instant                                             observedAt;
    }

    /** An additional projection joins the latest accepted complete view under the capture lock. */
    private boolean projectCurrentView(ActiveRepositoryInvocation active, Instant observedAt) {
        val observation = repositoryObservations.get(new RepositoryRoute(active.invocation()));
        if (observation == null || observation.registration == null) {
            return false;
        }
        observation.active.add(active);
        active.install(observation.latest, observedAt);
        return true;
    }

    void startRepositoryInvocation(ActiveRepositoryInvocation active) {
        val                   route = new RepositoryRoute(active.invocation());
        RepositoryObservation observation;
        boolean               acquire;
        lock.lock();
        try {
            if (closed || active.isClosed() || fallback == null) {
                return;
            }
            observation = repositoryObservations.get(route);
            acquire     = observation == null;
            if (acquire) {
                observation = new RepositoryObservation();
                repositoryObservations.put(route, observation);
            }
            observation.active.add(active);

        } finally {
            lock.unlock();
        }
        if (acquire) {
            acquireRepository(route, observation);
        }
        awaitEnrollment(observation.opened);
        if (!acquire) {
            val observedAt = observationTime();
            lock.lock();
            try {
                if (!active.isClosed() && repositoryObservations.get(route) == observation
                        && observation.registration != null) {
                    active.install(observation.latest, observedAt);
                }
            } finally {
                lock.unlock();
            }
        }
        dispatchValue(active);
    }

    private void acquireRepository(RepositoryRoute route, RepositoryObservation observation) {
        AttributeRepository.StateRegistration candidate = null;
        try {
            val listener = new AttributeRepository.StateListener(
                    state -> installRepositoryState(route, observation, state, BrokerSubscription::fireCallback),
                    state -> installRepositoryState(route, observation, state, BrokerSubscription::fireCallbackAsync));
            candidate = AttributeSourceCall
                    .invoke(() -> fallback.observeState(route.pdpId(), route.configurationId(), listener));
            val lease   = candidate;
            val initial = AttributeSourceCall.invoke(lease::initialState);
            listener.accept(initial);
            Set<BrokerSubscription> affected = Set.of();
            lock.lock();
            try {
                if (!closed && repositoryObservations.get(route) == observation) {
                    observation.registration = candidate;
                    candidate                = null;
                    affected                 = installProjections(observation);
                    observation.opened.complete(null);
                }
            } finally {
                lock.unlock();
            }
            affected.forEach(BrokerSubscription::fireCallback);
        } catch (RuntimeException failure) {
            failRepository(route, observation, null, failure, BrokerSubscription::fireCallback);
        } finally {
            if (candidate != null) {
                closeRegistration(route, candidate);
            }
        }
    }

    private boolean newer(RepositoryRoute route, RepositoryObservation observation, RepositoryState state) {
        return !closed && repositoryObservations.get(route) == observation
                && (observation.latest == null || state.revision() > observation.latest.revision());
    }

    private void installRepositoryState(RepositoryRoute route, RepositoryObservation observation, RepositoryState next,
            Consumer<BrokerSubscription> notifySubscriber) {
        lock.lock();
        try {
            if (!newer(route, observation, next)) {
                return;
            }
        } finally {
            lock.unlock();
        }
        Instant observedAt;
        try {
            observedAt = observationTime();
        } catch (RuntimeException failure) {
            failRepository(route, observation, next, failure, notifySubscriber);
            return;
        }
        Set<BrokerSubscription> affected;
        lock.lock();
        try {
            if (!newer(route, observation, next)) {
                return;
            }
            observation.latest     = next;
            observation.observedAt = observedAt;
            affected               = observation.registration == null ? Set.of() : installProjections(observation);
        } finally {
            lock.unlock();
        }
        affected.forEach(notifySubscriber);
    }

    private static Set<BrokerSubscription> installProjections(RepositoryObservation observation) {
        val affected = new HashSet<BrokerSubscription>();
        for (val active : observation.active) {
            if (active.install(observation.latest, observation.observedAt)) {
                affected.addAll(active.subscribers());
            }
        }
        affected.removeIf(subscription -> !subscription.isReadyForRound());
        return affected;
    }

    private void failRepository(RepositoryRoute route, RepositoryObservation observation,
            @Nullable RepositoryState failed, RuntimeException failure, Consumer<BrokerSubscription> notifySubscriber) {
        val                                   affected = new HashSet<BrokerSubscription>();
        AttributeRepository.StateRegistration registration;
        lock.lock();
        try {
            if (closed || repositoryObservations.get(route) != observation
                    || failed != null && !newer(route, observation, failed)) {
                return;
            }
            repositoryObservations.remove(route);
            registration = observation.registration;
            observation.opened.complete(failure);
            for (val active : observation.active) {
                active.retire(failure);
                val opening = openings.get(active);
                if (opening != null) {
                    opening.ready.complete(failure);
                }
                affected.addAll(active.subscribers());
                cancelPendingTeardown(active);
                removeFromList(active);
            }
            observation.active.clear();
            observation.registration = null;
            affected.forEach(handle -> handle.pendingFailure = failure);
        } finally {
            lock.unlock();
        }
        affected.forEach(notifySubscriber);
        if (registration != null) {
            closeRegistration(route, registration);
        }
    }

    private void closeRegistration(RepositoryRoute route, AttributeRepository.StateRegistration registration) {
        try {
            AttributeSourceCall.invoke(() -> {
                registration.close();
                return null;
            });
        } catch (RuntimeException failure) {
            log.error(ERROR_REPOSITORY_CLOSE, route.pdpId(), failure);
        }
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
                observation.opened.complete(null);
                toClose = observation.registration;
            }
        } finally {
            lock.unlock();
        }
        if (toClose != null) {
            closeRegistration(new RepositoryRoute(active.invocation()), toClose);
        }
    }

    private ActivePolicyInformationPointInvocation newTerminalActiveInvocation(AttributeFinderInvocation normalized) {
        val terminal = unstartedPip(normalized, null);
        terminal.publishImmediate(Value.UNDEFINED);
        return terminal;
    }

    /** Retires a failed producer or enrollment only while it owns the selected source. */
    private void failAdmission(ActiveInvocation active, @Nullable Enrollment expected, @Nullable SourceChange selection,
            RuntimeException failure) {
        List<BrokerSubscription> affected;
        lock.lock();
        try {
            val residents = activeInvocationsByInvocation.get(active.invocation());
            if (residents == null || !residents.contains(active) || expected != null && openings.get(active) != expected
                    || (expected != null || selection != null) && changingSources.get(active) != selection) {
                return;
            }
            val opening = openings.get(active);
            if (opening != null) {
                opening.ready.complete(failure);
            }
            cancelPendingTeardown(active);
            removeFromList(active);
            affected = List.copyOf(active.subscribers());
            affected.forEach(handle -> handle.pendingFailure = failure);
        } finally {
            lock.unlock();
        }
        affected.forEach(BrokerSubscription::fireCallback);
        release(active);
    }

    /**
     * Replaces {@code previous} with {@code replacement} in the per-invocation list
     * and in every subscribed consumer's
     * routing table. A prepared repository projection must still belong to a live
     * registration at this point. Subscriber refcount is preserved across the swap.
     * <p>
     * Used by catalog mutations to re-route active invocations when their source
     * changes. {@link #load} promotes
     * repository-backed or terminal active invocations to PIP-backed ones.
     * {@link #swap} or {@link PipHandle#unload}
     * demotes PIP-backed active invocations whose spec is gone to repository-backed
     * or terminal.
     */
    private boolean replaceRoutes(ActiveInvocation previous, ActiveInvocation replacement, SourceChange change) {
        lock.lock();
        try {
            if (closed || changingSources.get(previous) != change) {
                return false;
            }
            val residents = activeInvocationsByInvocation.get(previous.invocation());
            if (residents == null) {
                return false;
            }
            val index = residents.indexOf(previous);
            if (index < 0) {
                return false;
            }
            if (replacement instanceof ActiveRepositoryInvocation repository) {
                repository.checkAdmission();
            }
            if (replacement instanceof ActivePolicyInformationPointInvocation pip && previous.sourceSpec() != null
                    && replacement.sourceSpec() != null) {
                pip.retainObservation(previous.snapshot());
            }
            changingSources.remove(previous);
            residents.set(index, replacement);
            val opening = openings.remove(previous);
            if (opening != null) {
                opening.ready.complete(null);
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
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** A catalog handoff during a grace interval can create a replacement with no subscriber. */
    private void reclaimIfOrphaned(ActiveInvocation active) {
        boolean releaseNow;
        lock.lock();
        try {
            releaseNow = retireIfUnused(active);
        } finally {
            lock.unlock();
        }
        if (releaseNow) {
            release(active);
        }
    }

    /** Drops zero-grace ownership in the same transition as the last detach. */
    private boolean retireIfUnused(ActiveInvocation active) {
        val residents = activeInvocationsByInvocation.get(active.invocation());
        if (active.refcount() > 0 || residents == null || !residents.contains(active)) {
            return false;
        }
        if (!closed && residents.size() == 1 && !gracePeriodDuration.isZero()) {
            if (!pendingTeardowns.containsKey(active)) {
                scheduleTeardown(active);
            }
            return false;
        }
        cancelPendingTeardown(active);
        removeFromList(active);
        return true;
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
        return () -> AttributeSourceCall.invoke(() -> spec.attributeFinder().invoke(invocation));
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

    /**
     * Caller holds the broker lock. Removes the active invocation from its
     * invocation's list, dropping the list itself
     * when it becomes empty.
     */
    private void removeFromList(ActiveInvocation activeInvocation) {
        val opening = openings.remove(activeInvocation);
        if (opening != null) {
            opening.ready.complete(null);
        }
        changingSources.remove(activeInvocation);
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
            teardownTask.future.cancel(false);
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
        val interval = new GraceInterval();
        interval.future = teardownScheduler.schedule(() -> runScheduledTeardown(activeInvocation, interval),
                gracePeriodDuration.toMillis(), TimeUnit.MILLISECONDS);
        pendingTeardowns.put(activeInvocation, interval);
    }

    private static final class GraceInterval {
        private ScheduledFuture<?> future;
    }

    private void runScheduledTeardown(ActiveInvocation activeInvocation, GraceInterval interval) {
        lock.lock();

        try {
            if (!pendingTeardowns.remove(activeInvocation, interval)) {
                return;
            }
            if (activeInvocation.refcount() > 0) {
                return;
            }
            removeFromList(activeInvocation);
        } finally {

            lock.unlock();

        }
        release(activeInvocation);
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
        List<StreamAttributeFinderSpecification> evicted;
        List<SourceChange>                       changes;
        lock.lock();

        try {
            evicted = handleSpecs.remove(handle);
            if (evicted == null) {
                return;
            }
            handle.loaded.set(false);

            changes = selectChanges(Map.of(), evicted);
        } finally {

            lock.unlock();

        }
        changes.forEach(this::applyChange);
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

        private boolean                    opening = true;
        private boolean                    enrolled;
        private @Nullable RuntimeException pendingFailure;

        private void awaitEnrollment(SubscriptionKey key) {
            while (true) {
                ActiveInvocation active;
                Enrollment       pending;
                lock.lock();
                try {
                    if (pendingFailure != null) {
                        throw pendingFailure;
                    }
                    active  = routingTable.get(key);
                    pending = openings.get(active);
                } finally {
                    lock.unlock();
                }
                if (pending == null) {
                    return;
                }
                enroll(active, pending, true);
            }
        }

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
                refcountZeroed = detachUnderLock();
            } finally {

                lock.unlock();

            }
            for (val active : refcountZeroed) {
                release(active);
            }
            log.trace("Closed subscription '{}'", id);
        }

        private List<ActiveInvocation> detachUnderLock() {
            subscriptionClosed = true;
            consumerSubscriptions.remove(id, this);
            val zeroed = new ArrayList<ActiveInvocation>();
            for (val active : routingTable.values()) {
                if (active.detach(this) <= 0 && retireIfUnused(active)) {
                    zeroed.add(active);
                }
            }
            routingTable.clear();
            deliveredInputs.clear();
            dependencies = Set.of();
            return zeroed;
        }

        /**
         * Caller holds the routing lock. First values wait for the entire current
         * set. A new observation of a previously delivered live key can start a
         * partial round. Snapshot identity distinguishes renewals even when the
         * value and clock are equal, and suppresses already captured notifications.
         */
        boolean isReadyForRound() {
            if (subscriptionClosed || pendingFailure != null || opening) {
                return false;
            }
            boolean allFulfilled  = true;
            boolean hasFirstValue = false;
            for (val key : dependencies) {
                val active  = routingTable.get(key);
                val current = active == null ? null : admittedSnapshot(active).orElse(null);
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
         * PIP admissions use the same lock. Sample each physical invocation once,
         * including a pending one, so aliases share the observation and timestamp.
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
                observations.computeIfAbsent(active, PolicyInformationPointAttributeBroker.this::admittedSnapshot)
                        .ifPresent(snapshot -> snapshotsByKey.put(key, snapshot));
            }
            return Map.copyOf(snapshotsByKey);
        }

        void fireCallback() {
            if (!AttributeSourceCall.active()) {
                coalescer.requestFire();
            } else {
                fireCallbackAsync();
            }
        }

        private void fireCallbackAsync() {
            coalescer.requestFireAsync();
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
            RuntimeException                                                        failure;
            lock.lock();
            try {
                failure = pendingFailure;
                if (subscriptionClosed || opening && !enrolled) {
                    return;
                }
                opening = false;
                if (failure != null) {
                    snapshotsByKey = Map.of();
                    updateCallback = onUpdate;
                } else {
                    if (!isReadyForRound()) {
                        return;
                    }
                    snapshotsByKey = currentSnapshots();
                    deliveredInputs.putAll(snapshotsByKey);
                    updateCallback = onUpdate;
                }
            } finally {
                lock.unlock();
            }
            if (failure != null) {
                try {
                    if (updateCallback instanceof AttributeSubscriptionObserver observer) {
                        observer.onFailure().accept(failure);
                    }
                } finally {
                    close();
                }
                return;
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
            try {
                applyDependencyDiff(Set.copyOf(nextDependencies));
            } catch (RuntimeException setupFailure) {
                lock.lock();
                try {
                    pendingFailure = setupFailure;
                } finally {
                    lock.unlock();
                }
                fireCallback();
            }
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
            val changes = reconcile(nextDependencies, false);
            if (changes == null) {
                return;
            }
            for (val active : changes.retired()) {
                Thread.startVirtualThread(() -> release(active));
            }
            changes.openings().forEach((active, pending) -> enroll(active, pending, false));
            if (changes.ready()) {
                fireCallback();
            }
        }

        /**
         * Both initial interest and a returned dependency set use the same atomic routing transition.
         * A newly attached projection of a live repository route needs a first-observation time.
         * Release the routing lock before sampling. After reacquiring it, recheck the handle, the
         * source selection and the need for the timestamp, including when sampling failed.
         */
        private @Nullable RoutingChanges reconcile(Set<SubscriptionKey> next, boolean initial) {
            Instant          observedAt    = null;
            RuntimeException sampleFailure = null;
            while (true) {
                lock.lock();
                try {
                    if (initial) {
                        validateNewHandle();
                    } else if (closed || subscriptionClosed || pendingFailure != null || next.equals(dependencies)) {
                        return null;
                    }
                    if (observedAt != null || !needsProjectionTime(next)) {
                        if (initial) {
                            consumerSubscriptions.put(id, this);
                        }
                        return installRoutes(next, observedAt);
                    }
                    if (sampleFailure != null) {
                        throw sampleFailure;
                    }
                } finally {
                    lock.unlock();
                }
                try {
                    observedAt = observationTime();
                } catch (RuntimeException failure) {
                    sampleFailure = failure;
                }
            }
        }

        private boolean needsProjectionTime(Set<SubscriptionKey> keys) {
            if (closed || subscriptionClosed || repositoryObservations.isEmpty()) {
                return false;
            }
            for (val key : keys) {
                if (routingTable.containsKey(key)) {
                    continue;
                }
                val invocation = normalizeInvocation(key.invocation());
                val route      = repositoryObservations.get(new RepositoryRoute(invocation));
                if (route != null && route.registration != null
                        && (key.invocation().fresh() || !activeInvocationsByInvocation.containsKey(invocation))
                        && resolve(invocation).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private void validateNewHandle() {
            if (closed) {
                throw new IllegalStateException(ERROR_BROKER_CLOSED);
            }
            if (consumerSubscriptions.containsKey(id)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(id));
            }
        }

        private RoutingChanges installRoutes(Set<SubscriptionKey> next, @Nullable Instant observedAt) {
            val retired = new ArrayList<ActiveInvocation>();
            val pending = new LinkedHashMap<ActiveInvocation, Enrollment>();
            for (val key : next) {
                if (!routingTable.containsKey(key)) {
                    val active = activeInvocationFor(key, observedAt);
                    routingTable.put(key, active);
                    active.attach(this);
                    val enrollment = openings.get(active);
                    if (enrollment != null) {
                        pending.put(active, enrollment);
                    }
                }
            }
            val entries = routingTable.entrySet().iterator();
            while (entries.hasNext()) {
                val entry = entries.next();
                if (!next.contains(entry.getKey())) {
                    val active = entry.getValue();
                    if (active.detach(this) <= 0 && retireIfUnused(active)) {
                        retired.add(active);
                    }
                    deliveredInputs.remove(entry.getKey());
                    entries.remove();
                }
            }
            dependencies = next;
            return new RoutingChanges(retired, pending, isReadyForRound());
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
