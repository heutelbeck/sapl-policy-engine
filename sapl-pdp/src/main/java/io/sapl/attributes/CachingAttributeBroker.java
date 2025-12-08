/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes;

import io.sapl.api.attributes.*;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Caching attribute stream broker with thread-safe collection handling.
 * <p>
 * Uses CopyOnWriteArrayList for storing active streams to prevent
 * ConcurrentModificationException when streams are
 * added/removed by background threads during TTL expiration or PIP
 * hot-swapping.
 */
@Slf4j
@RequiredArgsConstructor
public class CachingAttributeBroker implements AttributeBroker {
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMillis(3000L);

    private final Map<AttributeFinderInvocation, List<AttributeStream>> activeStreamIndex    = new HashMap<>();
    private final Map<String, List<AttributeFinderSpecification>>       attributeFinderIndex = new HashMap<>();
    private final Map<String, PolicyInformationPointSpecification>      pipRegistry          = new HashMap<>();
    private final Map<String, List<String>>                             libraryToPipNamesMap = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private final AttributeRepository attributeRepository;

    @Getter
    private final List<Class<?>> registeredLibraries = new CopyOnWriteArrayList<>();

    @Override
    public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
        log.debug("Requesting stream for: '{}'", invocation);
        log.debug("Requesting stream for: '{}'", invocation.attributeName());

        synchronized (lock) {
            var streams = activeStreamIndex.computeIfAbsent(invocation, k -> new CopyOnWriteArrayList<>());

            if (!streams.isEmpty() && !invocation.fresh()) {
                val stream = streams.getFirst();
                val flux   = stream.getStream();

                if (flux != null) {
                    log.debug("Returning existing stream for: '{}'", invocation.attributeName());
                    return flux;
                }

                log.debug("Stream was disposed during grace period, removing and creating new for: '{}'",
                        invocation.attributeName());
                streams.remove(stream);
            }

            log.debug("Creating new stream for: '{}'", invocation.attributeName());
            val matchingSpecsAndPips = attributeFinderIndex.get(invocation.attributeName());
            log.debug("Name lookup for PIPs: {}", matchingSpecsAndPips);
            val newStream = newAttributeStream(invocation, matchingSpecsAndPips);
            streams.add(newStream);
            return newStream.getStream();
        }
    }

    /**
     * Creates a new AttributeStream for an invocation.
     *
     * @param invocation
     * an invocation
     * @param pipsWithNameOfInvocation
     * all PIPs with the same attribute name
     *
     * @return a new AttributeStream connected to a matching PIP, to the
     * AttributeRepository (if PIPs exist but none
     * match), or without a finder (emits error if no PIPs registered for that name)
     */
    private AttributeStream newAttributeStream(final AttributeFinderInvocation invocation,
            Iterable<AttributeFinderSpecification> pipsWithNameOfInvocation) {
        val uniquePipMatchingInvocation = searchForMatchingPip(invocation, pipsWithNameOfInvocation);
        if (null == uniquePipMatchingInvocation) {
            return new AttributeStream(invocation, this::removeAttributeStreamFromIndex, DEFAULT_GRACE_PERIOD);
        } else {
            return new AttributeStream(invocation, this::removeAttributeStreamFromIndex, DEFAULT_GRACE_PERIOD,
                    uniquePipMatchingInvocation);
        }
    }

    /**
     * Finds an AttributeFinder matching an invocation.
     * <p>
     * Searches for exact match first, then varargs match among the provided PIPs.
     * If PIPs are provided but none match
     * the signature, returns the AttributeRepository as fallback. If no PIPs are
     * provided (null), returns null to
     * signal no finder available.
     *
     * @param invocation
     * an invocation
     * @param pipsWithNameOfInvocation
     * PIPs with matching attribute name, or null
     *
     * @return matching PIP, AttributeRepository (fallback), or null (no PIPs for
     * that name)
     */
    private AttributeFinder searchForMatchingPip(AttributeFinderInvocation invocation,
            Iterable<AttributeFinderSpecification> pipsWithNameOfInvocation) {
        if (null == pipsWithNameOfInvocation) {
            return null;
        }
        AttributeFinder varArgsMatch = null;
        for (var spec : pipsWithNameOfInvocation) {
            val matchQuality = spec.matches(invocation);
            if (matchQuality == Match.EXACT_MATCH) {
                return spec.attributeFinder();
            } else if (matchQuality == Match.VARARGS_MATCH) {
                varArgsMatch = spec.attributeFinder();
            }
        }
        if (varArgsMatch != null) {
            return varArgsMatch;
        }
        return attributeRepository;
    }

    /**
     * Default callback for attribute streams upon destruction.
     * <p>
     * Thread-safe: CopyOnWriteArrayList allows concurrent modifications during
     * iteration.
     *
     * @param attributeStream
     * an attribute stream to remove
     */
    private void removeAttributeStreamFromIndex(AttributeStream attributeStream) {
        synchronized (lock) {
            val streams = activeStreamIndex.get(attributeStream.getInvocation());
            if (streams != null) {
                streams.remove(attributeStream);
            }
        }
    }

    public void loadPolicyInformationPoint(PolicyInformationPointImplementation pipImplementation) {
        synchronized (lock) {
            val pipSpecification = pipImplementation.specification();
            val pipName          = pipSpecification.name();
            if (pipRegistry.containsKey(pipName)) {
                throw new AttributeBrokerException(String.format(
                        "Namespace collision error. Policy Information Point with name %s already registered.",
                        pipName));
            }
            val varargsFindersForDelayedLoading = new ArrayList<AttributeFinderSpecification>();

            for (var attributeFinderSpecification : pipSpecification.attributeFinders()) {
                if (attributeFinderSpecification.hasVariableNumberOfArguments()) {
                    varargsFindersForDelayedLoading.add(attributeFinderSpecification);
                } else {
                    registerAttributeFinder(attributeFinderSpecification);
                }
            }
            for (var attributeFinderSpecification : varargsFindersForDelayedLoading) {
                registerAttributeFinder(attributeFinderSpecification);
            }
            pipRegistry.put(pipName, pipSpecification);
        }
    }

    /**
     * Registers a new PIP with the attribute broker. The specification must not
     * collide with any existing
     * specification. If there are any matching attribute streams consumed by
     * policies, the streams are connected to the
     * new PIP.
     *
     * @param attributeFinderSpecification
     * the specification of the PIP
     *
     * @throws AttributeBrokerException
     * if there is a specification collision
     */
    private void registerAttributeFinder(AttributeFinderSpecification attributeFinderSpecification) {
        log.debug("Publishing PIP: {}", attributeFinderSpecification);
        val pipName        = attributeFinderSpecification.fullyQualifiedName();
        var findersForName = attributeFinderIndex.computeIfAbsent(pipName, k -> new ArrayList<>());
        findersForName.add(attributeFinderSpecification);

        for (var invocationAndStreams : activeStreamIndex.entrySet()) {
            val invocation     = invocationAndStreams.getKey();
            val streams        = invocationAndStreams.getValue();
            val newFinderMatch = attributeFinderSpecification.matches(invocation);
            if (newFinderMatch == Match.EXACT_MATCH || (newFinderMatch == Match.VARARGS_MATCH
                    && !doesExactlyMatchingPipExist(findersForName, invocation))) {
                connectStreamsToPip(attributeFinderSpecification.attributeFinder(), streams);
            }
        }
    }

    private boolean doesExactlyMatchingPipExist(Iterable<AttributeFinderSpecification> pipsForName,
            final AttributeFinderInvocation invocation) {
        for (var spec : pipsForName) {
            val existingPipMatch = spec.matches(invocation);
            if (existingPipMatch == Match.EXACT_MATCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Connects streams to a PIP.
     * <p>
     * Thread-safe: Iterating over CopyOnWriteArrayList is safe even if the list is
     * modified concurrently by other
     * threads.
     *
     * @param policyInformationPoint
     * the PIP to connect
     * @param streams
     * the streams to connect
     */
    private void connectStreamsToPip(AttributeFinder policyInformationPoint, final Iterable<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.connectToPolicyInformationPoint(policyInformationPoint);
        }
    }

    public void unloadPolicyInformationPoint(String name) {
        synchronized (lock) {
            val pipToRemove = pipRegistry.get(name);
            if (null == pipToRemove) {
                return;
            }

            val nonVarargsFindersForDelayedRemoval = new ArrayList<AttributeFinderSpecification>();
            for (var finderForRemoval : pipToRemove.attributeFinders()) {
                if (finderForRemoval.hasVariableNumberOfArguments()) {
                    removeAttributeFinder(finderForRemoval);
                } else {
                    nonVarargsFindersForDelayedRemoval.add(finderForRemoval);
                }
            }
            for (var finderForRemoval : nonVarargsFindersForDelayedRemoval) {
                removeAttributeFinder(finderForRemoval);
            }

            // Remove PIP from registry
            pipRegistry.remove(name);
        }
    }

    /**
     * Removes a PIP with a given specification from the broker and disconnects all
     * connected attribute streams.
     *
     * @param attributeFinderSpecification
     * the specification of the PIP to remove
     */
    private void removeAttributeFinder(AttributeFinderSpecification attributeFinderSpecification) {
        log.debug("Unpublishing AttributeFinder: {}", attributeFinderSpecification);
        val attributeName = attributeFinderSpecification.fullyQualifiedName();
        val pipsForName   = attributeFinderIndex.get(attributeName);
        if (null == pipsForName) {
            return;
        }
        pipsForName.removeIf(spec -> spec.equals(attributeFinderSpecification));
        if (pipsForName.isEmpty()) {
            attributeFinderIndex.remove(attributeName);
        }

        for (var invocationAndStreams : activeStreamIndex.entrySet()) {
            val invocation      = invocationAndStreams.getKey();
            val streams         = invocationAndStreams.getValue();
            val removedPipMatch = attributeFinderSpecification.matches(invocation);

            if (removedPipMatch != Match.NO_MATCH) {
                disconnectStreams(streams);
            }

            if (removedPipMatch == Match.EXACT_MATCH) {
                for (var spec : pipsForName) {
                    if (spec.matches(invocation) == Match.VARARGS_MATCH) {
                        connectStreamsToPip(spec.attributeFinder(), streams);
                    }
                }
            }
        }
    }

    /**
     * Disconnects streams from their PIPs.
     * <p>
     * Thread-safe: Iterating over CopyOnWriteArrayList is safe even if the list is
     * modified concurrently.
     *
     * @param streams
     * the streams to disconnect
     */
    private void disconnectStreams(final Iterable<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.disconnectFromPolicyInformationPoint();
        }
    }

    /**
     * Loads a PIP library from a @PolicyInformationPoint annotated class.
     * <p>
     * <b>Atomicity:</b> All @Attribute methods are processed BEFORE any loading. If
     * ANY method fails, NOTHING is
     * loaded.
     * <p>
     * <b>Thread-safety:</b> Synchronized to prevent concurrent loads. Pre-checks
     * done outside lock for performance.
     * <p>
     * <b>Collision detection:</b> Checks library name, PIP name, and all attribute
     * names BEFORE loading anything.
     *
     * @param pipInstance
     * the PIP instance to load
     *
     * @throws AttributeBrokerException
     * if processing fails, library already loaded, or collision detected
     */
    public void loadPolicyInformationPointLibrary(Object pipInstance) {
        // STEP 1: Process everything OUTSIDE synchronized block
        // If this fails, nothing has been loaded yet
        PolicyInformationPointImplementation pipImpl;
        try {
            pipImpl = processPipClass(pipInstance);
        } catch (Exception e) {
            throw new AttributeBrokerException("Failed to process PIP class: " + e.getMessage(), e);
        }

        String                            libraryName = pipImpl.specification().name();
        String                            pipName     = pipImpl.specification().name(); // Same as library name
        Set<AttributeFinderSpecification> finders     = pipImpl.specification().attributeFinders();

        // STEP 2: Fast pre-check BEFORE lock (optimization)
        if (libraryToPipNamesMap.containsKey(libraryName)) {
            throw new AttributeBrokerException("Library already loaded: " + libraryName);
        }

        // STEP 3: ENTER synchronized block for collision checks + loading
        synchronized (lock) {
            // Double-check library not loaded (thread-safe)
            if (libraryToPipNamesMap.containsKey(libraryName)) {
                throw new AttributeBrokerException("Library already loaded: " + libraryName);
            }

            // Check PIP name collision
            if (pipRegistry.containsKey(pipName)) {
                throw new AttributeBrokerException("PIP name collision: " + pipName + " already registered");
            }

            // Check ALL attribute finder name collisions
            for (AttributeFinderSpecification finder : finders) {
                String                             attrName = finder.fullyQualifiedName();
                List<AttributeFinderSpecification> existing = attributeFinderIndex.get(attrName);

                if (existing != null) {
                    for (AttributeFinderSpecification existingSpec : existing) {
                        if (existingSpec.collidesWith(finder)) {
                            throw new AttributeBrokerException("Attribute collision: " + attrName + " with signature "
                                    + finder + " collides with existing " + existingSpec);
                        }
                    }
                }
            }

            // ALL CHECKS PASSED - now load the PIP
            // This uses existing hot-swap logic (reconnects streams, etc.)
            loadPolicyInformationPoint(pipImpl);

            // Track library
            libraryToPipNamesMap.put(libraryName, List.of(pipName));

            log.info("Loaded library '{}' with {} attributes", libraryName, finders.size());
        }
    }

    /**
     * Unloads a PIP library by name.
     * <p>
     * <b>Thread-safety:</b> Uses existing unloadPolicyInformationPoint which is
     * synchronized.
     *
     * @param libraryName
     * the name of the library to unload
     *
     * @return true if library was unloaded, false if not found
     */
    public boolean unloadPolicyInformationPointLibrary(String libraryName) {

        // Remove from library registry (atomic ConcurrentHashMap operation)
        List<String> pipNames = libraryToPipNamesMap.remove(libraryName);

        if (pipNames == null) {
            log.warn("Library '{}' not found, nothing to unload", libraryName);
            return false;
        }

        // Unload the PIP (uses existing synchronized method)
        synchronized (lock) {
            for (String pipName : pipNames) {
                unloadPolicyInformationPoint(pipName);
            }
        }

        log.info("Unloaded library '{}'", libraryName);
        return true;
    }

    /**
     * Returns set of loaded library names. Lock-free read from ConcurrentHashMap.
     *
     * @return set of loaded library names
     */
    public Set<String> getLoadedLibraryNames() {
        return libraryToPipNamesMap.keySet();
    }

    /**
     * Processes @PolicyInformationPoint class into implementation. Processes
     * ALL @Attribute methods. If ANY fails,
     * throws exception.
     * <p>
     * Called OUTSIDE synchronized block for performance.
     *
     * @param pipInstance
     * the PIP instance
     *
     * @return the processed PIP implementation
     *
     * @throws AttributeBrokerException
     * if processing fails
     */
    private PolicyInformationPointImplementation processPipClass(Object pipInstance) {

        Class<?> pipClass = pipInstance.getClass();
        registeredLibraries.add(pipClass);
        // Get @PolicyInformationPoint annotation
        PolicyInformationPoint annotation = pipClass.getAnnotation(PolicyInformationPoint.class);
        if (annotation == null) {
            throw new AttributeBrokerException(
                    "Class must be annotated with @PolicyInformationPoint: " + pipClass.getName());
        }

        String pipName = annotation.name();
        if (pipName == null || pipName.isBlank()) {
            throw new AttributeBrokerException("@PolicyInformationPoint.name() cannot be blank");
        }

        // Process ALL @Attribute methods
        Set<AttributeFinderSpecification> attributeFinders = new HashSet<>();

        for (Method method : pipClass.getDeclaredMethods()) {
            try {
                AttributeFinderSpecification spec = AttributeMethodSignatureProcessor
                        .processAttributeMethod(pipInstance, pipName, method);

                if (spec != null) {
                    attributeFinders.add(spec);
                }

            } catch (IllegalStateException e) {
                // Any method fails -> entire library fails
                throw new AttributeBrokerException("Failed to process method '" + method.getName() + "' in PIP '"
                        + pipName + "': " + e.getMessage(), e);
            }
        }

        // Validate: at least one attribute
        if (attributeFinders.isEmpty()) {
            throw new AttributeBrokerException(
                    "PIP '" + pipName + "' must have at least one @Attribute or @EnvironmentAttribute method");
        }

        // Construct specification
        PolicyInformationPointSpecification spec = new PolicyInformationPointSpecification(pipName, attributeFinders);

        return new PolicyInformationPointImplementation(spec);
    }

}
