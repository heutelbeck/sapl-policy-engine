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
 * Routing is static per backing. When a backing is created its
 * invocation is matched against the catalog: a PIP match produces a
 * {@link BackingSubscription} that observes the PIP exclusively; a
 * non-match produces a {@link DelegatedBacking} that observes the
 * fallback; no fallback yields a terminal UNDEFINED backing. The PIP
 * is authoritative for its invocations: a PIP's UNDEFINED is
 * propagated as UNDEFINED, never silently replaced by the fallback.
 * <p>
 * Catalog mutations migrate routing as needed: {@link #swap} that
 * keeps a spec rebinds the existing backing in place; {@link #swap}
 * or {@link PipHandle#unload} that removes a spec migrates the
 * backing to the fallback (or terminates it with UNDEFINED if no
 * fallback); {@link #load} that adds a spec promotes any matching
 * delegated or terminal backings to a PIP-backed backing.
 *
 * @since 4.1.0
 */
@Slf4j
public final class PolicyInformationPointAttributeBroker implements AttributeBroker {

    private static final String ERROR_ANNOTATION_MISSING      = "PIP class %s is not annotated with @PolicyInformationPoint.";
    private static final String ERROR_CATALOG_INVARIANT       = "catalog invariant violated: multiple exact matches for %s";
    private static final String ERROR_DEPS_EMPTY              = "initialDependencies must not be empty";
    private static final String ERROR_GRACE_DURATION_NEGATIVE = "gracePeriodDuration must not be negative";
    private static final String ERROR_HANDLE_NOT_LOADED       = "Cannot swap PIP: handle for '%s' is not currently loaded.";
    private static final String ERROR_LOAD_PROCESSOR_FAILED   = "Failed to register PIP '%s': %s";
    private static final String ERROR_PIP_INVOKE_FAILED       = "PIP invocation for attribute '%s' failed: %s";
    private static final String ERROR_REBIND_FAILED           = "Attribute '%s' rebind failed during hot-swap: %s";
    private static final String ERROR_RETURNED_DEPS_INVALID   = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SPEC_COLLISION          = "Cannot register PIP '%s': attribute '%s' (parameter shape %s) collides with already-registered PIP '%s'.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK   = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE  = "subscriptionId already open: %s";
    private static final String WARN_ONUPDATE_THREW           = "Consumer {} onUpdate threw: {}";

    private final Object                                                       lock             = new Object();
    private final Map<PipHandleImpl, List<StreamAttributeFinderSpecification>> handleSpecs      = new LinkedHashMap<>();
    private final Map<AttributeFinderInvocation, List<Backing>>                subscriptions    = new HashMap<>();
    private final Map<Backing, ScheduledFuture<?>>                             pendingTeardowns = new HashMap<>();
    private final Map<String, ConsumerSubscriptionImpl>                        consumers        = new HashMap<>();

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
     * PIP-served invocations are served exclusively by the PIP;
     * runtime UNDEFINED from a PIP is propagated as UNDEFINED, never
     * silently replaced by the fallback's value. Routing is decided
     * once when a backing is created and re-evaluated only on catalog
     * mutations ({@link #load}, {@link #swap}, {@link PipHandle#unload}).
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
        val           promotions = new LinkedHashMap<Backing, StreamAttributeFinderSpecification>();
        synchronized (lock) {
            checkCollisions(namespace, newSpecs, null);
            handle = new PipHandleImpl(namespace, documentation);
            handleSpecs.put(handle, List.copyOf(newSpecs));
            for (val backing : allLiveBackings()) {
                if (backing.sourceSpec() != null) {
                    continue;
                }
                val match = resolveLocked(backing.invocation());
                match.ifPresent(spec -> promotions.put(backing, spec));
            }
            log.debug("Loaded PIP '{}' with {} attribute(s)", namespace, newSpecs.size());
        }
        for (val entry : promotions.entrySet()) {
            val old         = entry.getKey();
            val replacement = createPipBacking(old.invocation(), entry.getValue());
            migrateBacking(old, replacement);
        }
        return handle;
    }

    /**
     * Atomically replaces the specs registered by {@code oldHandle}
     * with the specs contributed by {@code newInstance}. Backing
     * subscriptions whose source resolved through {@code oldHandle}
     * are rebound to the matching new spec without publishing a
     * transient value to their consumers. Specs that exist in
     * {@code oldHandle} but have no shape match in
     * {@code newInstance} are evicted: their backings publish
     * {@link Value#UNDEFINED} (absence). Specs new in
     * {@code newInstance} are available for new subscriptions.
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

        SwapPlan         plan;
        AffectedBackings affected;
        PipHandleImpl    newHandle;

        synchronized (lock) {
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

            affected = collectAffectedBackings(plan);
        }

        // Outside the lock: rebind / evict. invoke(...) on the new spec may throw or
        // block.
        val fb = fallback;
        for (val entry : affected.swappedBackings.entrySet()) {
            rebindBacking(entry.getKey(), entry.getValue());
        }
        for (val backing : affected.evictedBackings) {
            if (fb != null) {
                migrateBacking(backing, createDelegatedBacking(backing.invocation(), fb));
            } else {
                discardBacking(backing, Value.UNDEFINED);
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

    private AffectedBackings collectAffectedBackings(SwapPlan plan) {
        val swappedBackings = new LinkedHashMap<BackingSubscription, StreamAttributeFinderSpecification>();
        val evictedBackings = new ArrayList<BackingSubscription>();
        for (val backing : allLiveBackings()) {
            if (!(backing instanceof BackingSubscription pipBacking)) {
                continue;
            }
            val oldSpec = pipBacking.sourceSpec();
            if (oldSpec == null) {
                continue;
            }
            val newSpec = plan.swapped.get(oldSpec);
            if (newSpec != null) {
                swappedBackings.put(pipBacking, newSpec);
            } else if (plan.evictedOnly.contains(oldSpec)) {
                evictedBackings.add(pipBacking);
            }
        }
        return new AffectedBackings(swappedBackings, evictedBackings);
    }

    private record SwapPlan(
            Map<StreamAttributeFinderSpecification, StreamAttributeFinderSpecification> swapped,
            List<StreamAttributeFinderSpecification> evictedOnly,
            List<StreamAttributeFinderSpecification> addedOnly) {}

    private record AffectedBackings(
            Map<BackingSubscription, StreamAttributeFinderSpecification> swappedBackings,
            List<BackingSubscription> evictedBackings) {}

    /**
     * @return an unmodifiable snapshot of currently loaded handles
     */
    public Set<PipHandle> catalog() {
        synchronized (lock) {
            return Set.copyOf(handleSpecs.keySet());
        }
    }

    /**
     * @return the {@link LibraryDocumentation} for every PIP currently
     * in the catalog, suitable for IDE hover/completion and offline
     * documentation generation. Returned in load order; reflects the
     * catalog at the moment of the call.
     */
    public List<LibraryDocumentation> documentation() {
        synchronized (lock) {
            val docs = new ArrayList<LibraryDocumentation>(handleSpecs.size());
            for (val handle : handleSpecs.keySet()) {
                docs.add(handle.documentation());
            }
            return List.copyOf(docs);
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
        synchronized (lock) {
            return resolveLocked(invocation);
        }
    }

    /**
     * Caller holds the broker lock. Returns the spec serving
     * {@code invocation}, or {@link Optional#empty()} if no loaded PIP
     * matches.
     */
    private Optional<StreamAttributeFinderSpecification> resolveLocked(AttributeFinderInvocation invocation) {
        StreamAttributeFinderSpecification exact   = null;
        StreamAttributeFinderSpecification varargs = null;
        for (val specs : handleSpecs.values()) {
            for (val spec : specs) {
                val match = spec.matches(invocation);
                if (match == Match.EXACT_MATCH) {
                    if (exact != null) {
                        throw new IllegalStateException(ERROR_CATALOG_INVARIANT.formatted(invocation.attributeName()));
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

        ConsumerSubscriptionImpl consumer;
        boolean                  fireImmediately;
        synchronized (lock) {
            if (consumers.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            consumer = new ConsumerSubscriptionImpl(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            for (val key : initialDependencies) {
                val backing = resolveBackingForKey(key);
                consumer.route.put(key, backing);
                backing.attach(consumer);
            }
            consumers.put(subscriptionId, consumer);
            fireImmediately = consumer.tryFireGate();
        }
        log.trace("Opened subscription '{}' with {} dependency(ies)", subscriptionId, initialDependencies.size());
        if (fireImmediately) {
            consumer.fireCallback();
        }
        return consumer;
    }

    @Override
    public void close() {
        List<ConsumerSubscriptionImpl> toClose;
        Collection<Backing>            backingsToClose;
        synchronized (lock) {
            toClose         = new ArrayList<>(consumers.values());
            backingsToClose = new ArrayList<>(allLiveBackings());
            consumers.clear();
            subscriptions.clear();
            for (val task : pendingTeardowns.values()) {
                task.cancel(false);
            }
            pendingTeardowns.clear();
        }
        for (val c : toClose) {
            c.markClosed();
        }
        for (val b : backingsToClose) {
            b.close();
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
     * {@code fresh=true}: always creates a new backing and appends
     * it to the per-invocation list. {@code fresh=false}: attaches
     * to the first live entry in that list, cancelling any pending
     * teardown on it (warm reconnect during the grace window).
     * <p>
     * The list is keyed by the canonical invocation, which drops
     * the {@code fresh} flag. So fresh and non-fresh subscriptions
     * for an otherwise-identical invocation land in the same list:
     * a fresh stream stays private while it's alive, but a later
     * non-fresh consumer can attach to it once the original head
     * has been torn down.
     */
    private Backing resolveBackingForKey(SubscriptionKey key) {
        val invocation = key.invocation();
        val mapKey     = canonicalInvocation(invocation);
        val list       = subscriptions.computeIfAbsent(mapKey, k -> new ArrayList<>());
        if (invocation.fresh()) {
            val backing = openBacking(mapKey);
            list.add(backing);
            return backing;
        }
        for (val candidate : list) {
            if (!candidate.isClosed()) {
                cancelPendingTeardown(candidate);
                return candidate;
            }
        }
        val backing = openBacking(mapKey);
        list.add(backing);
        return backing;
    }

    /**
     * Returns the invocation with its {@code fresh} flag zeroed.
     * Used as the dedup-map key so consumers with different
     * {@code fresh} values for the same underlying attribute can
     * share a backing list.
     */
    private static AttributeFinderInvocation canonicalInvocation(AttributeFinderInvocation invocation) {
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
     * Routes by catalog state at open time. A PIP match yields a
     * {@link BackingSubscription} fed by an {@link AttributeStream}
     * (poll / retry / timeout cycle around the PIP). No PIP match
     * and a configured fallback yields a {@link DelegatedBacking}
     * observing the fallback. No PIP match and no fallback yields a
     * terminal backing preloaded with {@link Value#UNDEFINED}.
     * <p>
     * The PIP receives the canonical invocation (with {@code fresh}
     * zeroed). {@code fresh} is a broker-internal flag and never
     * leaves this layer.
     */
    private Backing openBacking(AttributeFinderInvocation canonical) {
        val specOpt = resolveLocked(canonical);
        if (specOpt.isPresent()) {
            val backing = createPipBacking(canonical, specOpt.get());
            backing.start();
            return backing;
        }
        val fb = fallback;
        if (fb != null) {
            val backing = createDelegatedBacking(canonical, fb);
            backing.start();
            return backing;
        }
        return createTerminalBacking(canonical);
    }

    private BackingSubscription createPipBacking(AttributeFinderInvocation canonical,
            StreamAttributeFinderSpecification spec) {
        val                     holder     = new BackingSubscription[1];
        Supplier<Stream<Value>> innerOpen  = innerStreamSupplier(spec, canonical);
        val                     firstInner = innerOpen.get();
        val                     stream     = new AttributeStream(canonical, firstInner, innerOpen);
        holder[0] = new BackingSubscription(canonical, stream, spec, v -> dispatchValue(holder[0]));
        return holder[0];
    }

    private DelegatedBacking createDelegatedBacking(AttributeFinderInvocation canonical,
            AttributeRepository repository) {
        val holder = new DelegatedBacking[1];
        holder[0] = new DelegatedBacking(canonical, repository, v -> dispatchValue(holder[0]));
        return holder[0];
    }

    private BackingSubscription createTerminalBacking(AttributeFinderInvocation canonical) {
        val holder = new BackingSubscription[1];
        holder[0] = new BackingSubscription(canonical, null, null, v -> dispatchValue(holder[0]));
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
     * Used by catalog mutations to re-route active backings when their
     * source changes: {@link #load} promotes delegated or terminal
     * backings to PIP-backed; {@link #swap} or {@link PipHandle#unload}
     * demotes PIP-backed backings whose spec is gone to delegated or
     * terminal.
     */
    private void migrateBacking(Backing old, Backing replacement) {
        synchronized (lock) {
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
     * Returns a snapshot of every live backing across all invocation
     * lists. Caller holds the broker lock.
     */
    private List<Backing> allLiveBackings() {
        val all = new ArrayList<Backing>();
        for (val list : subscriptions.values()) {
            all.addAll(list);
        }
        return all;
    }

    private void rebindBacking(BackingSubscription backing, StreamAttributeFinderSpecification newSpec) {
        val           supplier   = innerStreamSupplier(newSpec, backing.invocation());
        val           firstInner = supplier.get();
        Stream<Value> newStream  = new AttributeStream(backing.invocation(), firstInner, supplier);
        backing.rebind(newStream, newSpec);
    }

    /**
     * Publishes {@code terminalValue} to the backing's mailbox, removes
     * it from the per-invocation list and any pending teardown
     * registration, and closes it. Used for catalog mutations that
     * remove the serving PIP (publish UNDEFINED: absence) and for
     * rebind failures (publish ErrorValue: tried and failed).
     */
    private void discardBacking(BackingSubscription backing, Value terminalValue) {
        backing.publishImmediate(terminalValue);
        synchronized (lock) {
            removeFromList(backing);
            cancelPendingTeardown(backing);
        }
        backing.close();
    }

    /**
     * Caller holds the broker lock. Removes the backing from its
     * invocation's list, dropping the list itself when it becomes
     * empty.
     */
    private void removeFromList(Backing backing) {
        val list = subscriptions.get(backing.invocation());
        if (list == null) {
            return;
        }
        list.remove(backing);
        if (list.isEmpty()) {
            subscriptions.remove(backing.invocation());
        }
    }

    /**
     * Caller holds the broker lock. Cancels a scheduled grace-period
     * teardown for {@code backing}, if one is pending.
     */
    private void cancelPendingTeardown(Backing backing) {
        val task = pendingTeardowns.remove(backing);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Called when a backing's refcount drops to zero.
     * <p>
     * Three cases:
     * <ul>
     * <li>The per-invocation list has more than one entry: tear
     * down immediately. The other entries already serve future
     * {@code fresh=false} arrivals, so a grace window here would
     * just delay reclaiming the resource.</li>
     * <li>Grace duration is zero: tear down immediately.</li>
     * <li>Otherwise: schedule teardown after the grace duration.
     * The backing stays in the list during the window; a
     * re-attaching {@code fresh=false} consumer cancels the
     * scheduled teardown and reuses the warm connection.</li>
     * </ul>
     */
    private void handleRefcountZero(Backing backing) {
        boolean teardownNow = false;
        synchronized (lock) {
            if (backing.refcount() > 0) {
                return;
            }
            val list = subscriptions.get(backing.invocation());
            if (list != null && list.size() > 1) {
                teardownNow = true;
            } else if (gracePeriodDuration.isZero()) {
                teardownNow = true;
            } else {
                scheduleTeardown(backing);
                return;
            }
            removeFromList(backing);
        }
        if (teardownNow) {
            backing.close();
        }
    }

    /**
     * Caller holds the broker lock. Schedules the backing for teardown
     * after the configured grace duration. The task is idempotent
     * against cancellation via {@link #pendingTeardowns}.
     */
    private void scheduleTeardown(Backing backing) {
        val task = teardownScheduler.schedule(() -> runScheduledTeardown(backing), gracePeriodDuration.toMillis(),
                TimeUnit.MILLISECONDS);
        pendingTeardowns.put(backing, task);
    }

    private void runScheduledTeardown(Backing backing) {
        boolean toClose = false;
        synchronized (lock) {
            if (pendingTeardowns.remove(backing) == null) {
                return;
            }
            if (backing.refcount() > 0) {
                return;
            }
            removeFromList(backing);
            toClose = true;
        }
        if (toClose) {
            backing.close();
        }
    }

    /**
     * Called by a {@link Backing} on each value publication. Iterates
     * only this backing's own subscriber set (a reverse index
     * maintained by attach/detach), so dispatch is
     * O(consumers-of-this-backing), not O(all-consumers).
     */
    private void dispatchValue(Backing backing) {
        List<ConsumerSubscriptionImpl> toFire;
        synchronized (lock) {
            toFire = new ArrayList<>();
            for (val consumer : backing.subscribers()) {
                if (consumer.tryFireGate()) {
                    toFire.add(consumer);
                }
            }
        }
        for (val c : toFire) {
            c.fireCallback();
        }
    }

    void unloadHandle(PipHandleImpl handle) {
        List<StreamAttributeFinderSpecification> evicted;
        List<BackingSubscription>                evictedBackings;
        synchronized (lock) {
            evicted = handleSpecs.remove(handle);
            if (evicted == null) {
                return;
            }
            handle.loaded.set(false);

            evictedBackings = new ArrayList<>();
            for (val backing : allLiveBackings()) {
                if (!(backing instanceof BackingSubscription pipBacking)) {
                    continue;
                }
                val oldSpec = pipBacking.sourceSpec();
                if (oldSpec != null && evicted.contains(oldSpec)) {
                    evictedBackings.add(pipBacking);
                }
            }
        }
        val fb = fallback;
        for (val backing : evictedBackings) {
            if (fb != null) {
                migrateBacking(backing, createDelegatedBacking(backing.invocation(), fb));
            } else {
                discardBacking(backing, Value.UNDEFINED);
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
    final class ConsumerSubscriptionImpl implements Subscription {

        final String                                                                  id;
        final DispatchCoalescer                                                       coalescer;
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          deps;
        Map<SubscriptionKey, Backing>                                                 route    = new HashMap<>();
        boolean                                                                       gateOpen = false;
        boolean                                                                       closed   = false;

        ConsumerSubscriptionImpl(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id        = id;
            this.deps      = deps;
            this.onUpdate  = onUpdate;
            this.coalescer = new DispatchCoalescer(this::runOneFire);
        }

        @Override
        public void close() {
            List<Backing> zeroed;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                consumers.remove(id);
                zeroed = new ArrayList<>();
                for (val backing : route.values()) {
                    if (backing.detach(this) <= 0) {
                        zeroed.add(backing);
                    }
                }
                route.clear();
            }
            for (val backing : zeroed) {
                handleRefcountZero(backing);
            }
            log.trace("Closed subscription '{}'", id);
        }

        void markClosed() {
            synchronized (lock) {
                closed = true;
            }
        }

        /**
         * Caller holds the broker lock. Returns {@code true} iff
         * every current dep has a value in its backing.
         */
        boolean allDepsHaveValues() {
            for (val key : deps) {
                val backing = route.get(key);
                if (backing == null || backing.snapshot().isEmpty()) {
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
                val backing = route.get(key);
                if (backing == null) {
                    continue;
                }
                backing.snapshot().ifPresent(value -> result.put(key, new AttributeSnapshot(value, now)));
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
            synchronized (lock) {
                if (closed) {
                    return;
                }
                snapshot = currentSnapshot();
                cb       = onUpdate;
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
            List<Backing> zeroed = new ArrayList<>();
            boolean       refire;
            synchronized (lock) {
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
                    val backing = resolveBackingForKey(key);
                    route.put(key, backing);
                    backing.attach(this);
                }
                for (val key : removed) {
                    val backing = route.remove(key);
                    if (backing != null && backing.detach(this) <= 0) {
                        zeroed.add(backing);
                    }
                }
                deps     = new HashSet<>(newDeps);
                gateOpen = allDepsHaveValues();
                refire   = gateOpen && added.stream().anyMatch(k -> route.get(k).snapshot().isPresent());
            }
            for (val backing : zeroed) {
                handleRefcountZero(backing);
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
