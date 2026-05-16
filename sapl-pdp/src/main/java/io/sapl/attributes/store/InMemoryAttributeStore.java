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
package io.sapl.attributes.store;

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
import io.sapl.documentation.LibraryDocumentationExtractor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.reflect.Method;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * In-memory {@link AttributeStore} that also owns the catalog of
 * loaded Policy Information Points. The {@link AttributeStore}
 * interface defines the consumer contract (subscription open/close,
 * snapshot callbacks); the public methods on this concrete class
 * ({@link #load}, {@link #swap}, {@link #catalog}) are the runtime
 * configuration / plugin-engine surface. Same object, two surfaces.
 * <p>
 * Catalog mutations are atomic: a failed {@link #load} or
 * {@link #swap} leaves the catalog byte-for-byte identical to the
 * state before the call. The collision rule is enforced at load time
 * via {@link StreamAttributeFinderSpecification#collidesWith}, so
 * runtime resolution is unambiguous by construction.
 * <p>
 * Hot-swap rebinds active backing subscriptions to the replacement
 * specs without publishing a transient value: consumer mailboxes hold
 * the prior value through the source rebind and transition to the new
 * value when the new stream emits. Specs evicted by a swap (no shape
 * match in the replacement) or by an unload publish
 * {@link Value#UNDEFINED} to their backings: absence at this layer,
 * which a {@code LayeredAttributeStore} can fall through to a
 * repository.
 *
 * @since 4.1.0
 */
@Slf4j
public final class InMemoryAttributeStore implements AttributeStore {

    private static final String ERROR_ANNOTATION_MISSING     = "PIP class %s is not annotated with @PolicyInformationPoint.";
    private static final String ERROR_DEPS_EMPTY             = "initialDependencies must not be empty";
    private static final String ERROR_HANDLE_NOT_LOADED      = "Cannot swap PIP: handle for '%s' is not currently loaded.";
    private static final String ERROR_LOAD_PROCESSOR_FAILED  = "Failed to register PIP '%s': %s";
    private static final String ERROR_PIP_INVOKE_FAILED      = "PIP invocation for attribute '%s' failed: %s";
    private static final String ERROR_REBIND_FAILED          = "Attribute '%s' rebind failed during hot-swap: %s";
    private static final String ERROR_RETURNED_DEPS_INVALID  = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SPEC_COLLISION         = "Cannot register PIP '%s': attribute '%s' (parameter shape %s) collides with already-registered PIP '%s'.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK  = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE = "subscriptionId already open: %s";

    private final Object                                                       lock                 = new Object();
    private final Map<PipHandleImpl, List<StreamAttributeFinderSpecification>> handleSpecs          = new LinkedHashMap<>();
    private final Map<AttributeFinderInvocation, BackingSubscription>          sharedSubscriptions  = new HashMap<>();
    private final Set<BackingSubscription>                                     privateSubscriptions = new HashSet<>();
    private final Map<String, ConsumerSubscriptionImpl>                        consumers            = new HashMap<>();

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

        synchronized (lock) {
            checkCollisions(namespace, newSpecs, null);
            val handle = new PipHandleImpl(namespace, documentation);
            handleSpecs.put(handle, List.copyOf(newSpecs));
            log.debug("Loaded PIP '{}' with {} attribute(s)", namespace, newSpecs.size());
            return handle;
        }
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
        if (!(oldHandle instanceof PipHandleImpl old) || old.store() != this) {
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
        for (val entry : affected.swappedBackings.entrySet()) {
            rebindBacking(entry.getKey(), entry.getValue());
        }
        for (val backing : affected.evictedBackings) {
            discardBacking(backing, Value.UNDEFINED);
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
        for (val backing : allBackings()) {
            val oldSpec = backing.sourceSpec();
            if (oldSpec == null) {
                continue;
            }
            val newSpec = plan.swapped.get(oldSpec);
            if (newSpec != null) {
                swappedBackings.put(backing, newSpec);
            } else if (plan.evictedOnly.contains(oldSpec)) {
                evictedBackings.add(backing);
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
        StreamAttributeFinderSpecification exact   = null;
        StreamAttributeFinderSpecification varargs = null;
        synchronized (lock) {
            for (val specs : handleSpecs.values()) {
                for (val spec : specs) {
                    val match = spec.matches(invocation);
                    if (match == Match.EXACT_MATCH) {
                        if (exact != null) {
                            throw new IllegalStateException("catalog invariant violated: multiple exact matches for "
                                    + invocation.attributeName());
                        }
                        exact = spec;
                    } else if (match == Match.VARARGS_MATCH && varargs == null) {
                        varargs = spec;
                    }
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
                backing.attach();
            }
            consumers.put(subscriptionId, consumer);
            fireImmediately = consumer.allDepsHaveValues();
            if (fireImmediately) {
                consumer.gateOpen = true;
            }
        }
        log.trace("Opened subscription '{}' with {} dependency(ies)", subscriptionId, initialDependencies.size());
        if (fireImmediately) {
            consumer.fireCallback();
        }
        return consumer;
    }

    @Override
    public void close() {
        List<ConsumerSubscriptionImpl>  toClose;
        Collection<BackingSubscription> backingsToClose;
        synchronized (lock) {
            toClose         = new ArrayList<>(consumers.values());
            backingsToClose = new ArrayList<>(sharedSubscriptions.values());
            backingsToClose.addAll(privateSubscriptions);
            consumers.clear();
            sharedSubscriptions.clear();
            privateSubscriptions.clear();
        }
        for (val c : toClose) {
            c.markClosed();
        }
        for (val b : backingsToClose) {
            b.close();
        }
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
     * Caller holds the store lock.
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
     * Resolves a SubscriptionKey to its backing subscription. Caller
     * holds the store lock.
     */
    private BackingSubscription resolveBackingForKey(SubscriptionKey key) {
        val invocation = key.invocation();
        if (invocation.fresh()) {
            val backing = openBacking(invocation, false);
            privateSubscriptions.add(backing);
            return backing;
        }
        val existing = sharedSubscriptions.get(invocation);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        val backing = openBacking(invocation, true);
        sharedSubscriptions.put(invocation, backing);
        return backing;
    }

    /**
     * Opens a backing subscription for the invocation. If a PIP in the
     * catalog matches, the backing pumps from the PIP's
     * {@link Stream}. If no PIP matches, the backing is terminal with
     * the mailbox preloaded to {@link Value#UNDEFINED}. A later catalog
     * mutation (load/swap) can rebind such a terminal backing to a
     * newly-matching PIP. Caller holds the store lock.
     */
    private BackingSubscription openBacking(AttributeFinderInvocation invocation, boolean shared) {
        val holder  = new BackingSubscription[1];
        val specOpt = resolve(invocation);
        if (specOpt.isEmpty()) {
            holder[0] = new BackingSubscription(invocation, shared, null, null, v -> dispatchValue(holder[0]));
            holder[0].publishImmediate(Value.UNDEFINED);
            return holder[0];
        }
        val           spec = specOpt.get();
        Stream<Value> stream;
        try {
            stream = spec.attributeFinder().invoke(invocation);
        } catch (RuntimeException e) {
            log.debug("Attribute finder for '{}' threw on invoke: {}", invocation.attributeName(), e.getMessage());
            stream = Streams
                    .just(Value.error(ERROR_PIP_INVOKE_FAILED.formatted(invocation.attributeName(), e.getMessage())));
        }
        holder[0] = new BackingSubscription(invocation, shared, stream, spec, v -> dispatchValue(holder[0]));
        holder[0].start();
        return holder[0];
    }

    private List<BackingSubscription> allBackings() {
        val all = new ArrayList<BackingSubscription>(sharedSubscriptions.size() + privateSubscriptions.size());
        all.addAll(sharedSubscriptions.values());
        all.addAll(privateSubscriptions);
        return all;
    }

    private void rebindBacking(BackingSubscription backing, StreamAttributeFinderSpecification newSpec) {
        Stream<Value> newStream;
        try {
            newStream = newSpec.attributeFinder().invoke(backing.invocation());
        } catch (RuntimeException e) {
            log.debug("Rebind invoke for '{}' threw: {}", backing.invocation().attributeName(), e.getMessage());
            discardBacking(backing,
                    Value.error(ERROR_REBIND_FAILED.formatted(backing.invocation().attributeName(), e.getMessage())));
            return;
        }
        backing.rebind(newStream, newSpec);
    }

    /**
     * Publishes {@code terminalValue} to the backing's mailbox, removes
     * it from the dedup/private tables, and closes it. Used for catalog
     * mutations that remove the serving PIP (publish UNDEFINED:
     * absence) and for rebind failures (publish ErrorValue: tried and
     * failed).
     */
    private void discardBacking(BackingSubscription backing, Value terminalValue) {
        backing.publishImmediate(terminalValue);
        synchronized (lock) {
            if (backing.shared()) {
                sharedSubscriptions.remove(backing.invocation(), backing);
            } else {
                privateSubscriptions.remove(backing);
            }
        }
        backing.close();
    }

    /**
     * Called by a {@link BackingSubscription} on each value
     * publication. Walks consumers, fires those whose route includes
     * this backing.
     */
    private void dispatchValue(BackingSubscription backing) {
        List<ConsumerSubscriptionImpl> toFire;
        synchronized (lock) {
            toFire = new ArrayList<>();
            for (val consumer : consumers.values()) {
                if (consumer.routesAnyKeyTo(backing)) {
                    if (!consumer.gateOpen && consumer.allDepsHaveValues()) {
                        consumer.gateOpen = true;
                        toFire.add(consumer);
                    } else if (consumer.gateOpen) {
                        toFire.add(consumer);
                    }
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
            for (val backing : allBackings()) {
                val oldSpec = backing.sourceSpec();
                if (oldSpec != null && evicted.contains(oldSpec)) {
                    evictedBackings.add(backing);
                }
            }
        }
        for (val backing : evictedBackings) {
            discardBacking(backing, Value.UNDEFINED);
        }
        log.debug("Unloaded PIP '{}' ({} attribute(s) removed)", handle.pipName(), evicted.size());
    }

    /**
     * Subscription handle for one consumer. Owns the per-consumer
     * routing table, the gate state, and the callback. Serializes
     * onUpdate firing through {@link #callbackLock}.
     */
    final class ConsumerSubscriptionImpl implements Subscription {

        final String                                                                  id;
        final ReentrantLock                                                           callbackLock = new ReentrantLock();
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          deps;
        Map<SubscriptionKey, BackingSubscription>                                     route        = new HashMap<>();
        boolean                                                                       gateOpen     = false;
        boolean                                                                       closed       = false;

        ConsumerSubscriptionImpl(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id       = id;
            this.deps     = deps;
            this.onUpdate = onUpdate;
        }

        @Override
        public void close() {
            List<BackingSubscription> backingsToTeardown = new ArrayList<>();
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                consumers.remove(id);
                for (val backing : route.values()) {
                    if (backing.detach() <= 0) {
                        backingsToTeardown.add(backing);
                        if (backing.shared()) {
                            sharedSubscriptions.remove(backing.invocation(), backing);
                        } else {
                            privateSubscriptions.remove(backing);
                        }
                    }
                }
                route.clear();
            }
            for (val backing : backingsToTeardown) {
                backing.close();
            }
            log.trace("Closed subscription '{}'", id);
        }

        void markClosed() {
            synchronized (lock) {
                closed = true;
            }
        }

        boolean allDepsHaveValues() {
            for (val key : deps) {
                val backing = route.get(key);
                if (backing == null || backing.snapshot(key.head()).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        boolean routesAnyKeyTo(BackingSubscription backing) {
            for (val routed : route.values()) {
                if (routed == backing) {
                    return true;
                }
            }
            return false;
        }

        Map<SubscriptionKey, AttributeSnapshot> currentSnapshot() {
            val now    = Instant.now();
            val result = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(deps.size());
            for (val key : deps) {
                val backing = route.get(key);
                if (backing == null) {
                    continue;
                }
                val v = backing.snapshot(key.head());
                v.ifPresent(value -> result.put(key, new AttributeSnapshot(value, now)));
            }
            return Map.copyOf(result);
        }

        void fireCallback() {
            callbackLock.lock();
            try {
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
                    log.warn("Consumer {} onUpdate threw: {}", id, e.getMessage(), e);
                    return;
                }
                if (newDeps == null || newDeps.isEmpty()) {
                    throw new IllegalStateException(ERROR_RETURNED_DEPS_INVALID.formatted(id));
                }
                applyDepDiff(newDeps);
            } finally {
                callbackLock.unlock();
            }
        }

        private void applyDepDiff(Set<SubscriptionKey> newDeps) {
            boolean refire;
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
                    backing.attach();
                }
                for (val key : removed) {
                    val backing = route.remove(key);
                    if (backing != null && backing.detach() <= 0) {
                        if (backing.shared()) {
                            sharedSubscriptions.remove(backing.invocation(), backing);
                        } else {
                            privateSubscriptions.remove(backing);
                        }
                        backing.close();
                    }
                }
                deps     = new HashSet<>(newDeps);
                gateOpen = allDepsHaveValues();
                refire   = gateOpen && !added.stream().allMatch(k -> route.get(k).snapshot(k.head()).isEmpty());
            }
            if (refire) {
                fireCallback();
            }
        }
    }

    /**
     * Internal handle implementation. Tied to its enclosing store
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

        InMemoryAttributeStore store() {
            return InMemoryAttributeStore.this;
        }

        @Override
        public String toString() {
            return "PipHandle[" + pipName + (loaded.get() ? "" : " unloaded") + "]";
        }
    }
}
