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
package io.sapl.attributes.broker.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.*;
import io.sapl.attributes.broker.api.AttributeFinderSpecification.Match;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CachingAttributeStreamBroker implements AttributeStreamBroker {
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMillis(3000L);

    private record SpecificationAndFinder(
            AttributeFinderSpecification specification,
            AttributeFinder policyInformationPoint) {}

    private final Map<AttributeFinderInvocation, List<AttributeStream>> activeStreamIndex    = new HashMap<>();
    private final Map<String, List<SpecificationAndFinder>>             attributeFinderIndex = new HashMap<>();
    private final Map<String, PolicyInformationPointSpecification>      pipRegistry          = new HashMap<>();

    private final Object lock = new Object();

    @Override
    public Flux<Val> attributeStream(AttributeFinderInvocation invocation) {
        log.debug("Requesting stream for: '{}'", invocation.attributeName());
        synchronized (lock) {
            var streams = activeStreamIndex.computeIfAbsent(invocation, k -> new ArrayList<>());

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
            val newStream            = newAttributeStream(invocation, matchingSpecsAndPips);
            streams.add(newStream);
            return newStream.getStream();
        }
    }

    /**
     * Create a new AttributeStream for an invocation.
     *
     * @param invocation an invocation
     * @param pipsWithNameOfInvocation all PIPs with the same name.
     * @return a new AttributeStream, which is connected to a matching PIP is
     * present. Else directly, an error is published in the stream that no PIP
     * was found for the invocation.
     */
    private AttributeStream newAttributeStream(final AttributeFinderInvocation invocation,
            Iterable<SpecificationAndFinder> pipsWithNameOfInvocation) {
        val uniquePipMatchingInvocation = searchForMatchingPip(invocation, pipsWithNameOfInvocation);
        if (null == uniquePipMatchingInvocation) {
            return new AttributeStream(invocation, this::removeAttributeStreamFromIndex, DEFAULT_GRACE_PERIOD);
        } else {
            return new AttributeStream(invocation, this::removeAttributeStreamFromIndex, DEFAULT_GRACE_PERIOD,
                    uniquePipMatchingInvocation);
        }
    }

    /**
     * Find a PIP with specification that matches an invocation in a list.
     *
     * @param invocation an invocation
     * @param pipsWithNameOfInvocation a List of PIPs with specification.
     * @return a PIP whose specification is matching the invocation, or null.
     */
    private AttributeFinder searchForMatchingPip(AttributeFinderInvocation invocation,
            Iterable<SpecificationAndFinder> pipsWithNameOfInvocation) {
        if (null == pipsWithNameOfInvocation) {
            return null;
        }
        AttributeFinder varArgsMatch = null;
        for (var specAndPip : pipsWithNameOfInvocation) {
            val matchQuality = specAndPip.specification().matches(invocation);
            if (matchQuality == Match.EXACT_MATCH) {
                // return exact matches early even if a varargs match already was found.
                return specAndPip.policyInformationPoint();
            } else if (matchQuality == Match.VARARGS_MATCH) {
                varArgsMatch = specAndPip.policyInformationPoint();
            }
        }
        return varArgsMatch;
    }

    /**
     * Default callback for attribute streams upon destruction.
     *
     * @param attributeStream An attribute stream to remove.
     */
    private void removeAttributeStreamFromIndex(AttributeStream attributeStream) {
        synchronized (lock) {
            val streams = activeStreamIndex.get(attributeStream.getInvocation());
            streams.remove(attributeStream);
        }
    }

    @Override
    public void loadPolicyInformationPoint(PolicyInformationPointImplementation pipImplementation) {
        synchronized (lock) {
            val pipSpecification = pipImplementation.specification();
            val pipName          = pipSpecification.name();
            if (pipRegistry.containsKey(pipName)) {
                throw new AttributeBrokerException(String.format(
                        "Namespace collision error. Policy Information Point with name %s already registered.",
                        pipName));
            }
            val finderImplementations           = pipImplementation.implementations();
            val varargsFindersForDelayedLoading = new ArrayList<AttributeFinderSpecification>();
            // Explanation: First load the non-varargs finders. If we loaded a varargs
            // first, this may trigger a match with an existing subscription, and it would
            // wrongly subscribe to it even if there also exist an exact match which has
            // not been seen while loading yet. If you fist load the exact matches, the
            // varargs match will not override it and the loading of the library is
            // seemingly atomic from the perspective of existing subscriptions.
            for (var attributeFinderSpecification : pipSpecification.attributeFinders()) {
                if (attributeFinderSpecification.hasVariableNumberOfArguments()) {
                    varargsFindersForDelayedLoading.add(attributeFinderSpecification);
                } else {
                    val attributeFinder = finderImplementations.get(attributeFinderSpecification);
                    registerAttributeFinder(attributeFinderSpecification, attributeFinder);
                }
            }
            for (var attributeFinderSpecification : varargsFindersForDelayedLoading) {
                val attributeFinder = finderImplementations.get(attributeFinderSpecification);
                registerAttributeFinder(attributeFinderSpecification, attributeFinder);
            }
            pipRegistry.put(pipName, pipSpecification);
        }
    }

    /**
     * This method registers a new PIP with the attribute broker. The specification
     * must not collide with any existing specification. If there are any matching
     * attribute streams consumed by policies, the streams are connected to the new
     * PIP.
     *
     * @param attributeFinderSpecification The specification of the PIP.
     * @param attributeFinder The PIP itself.
     * @throws AttributeBrokerException if there is a specification collision.
     */
    private void registerAttributeFinder(AttributeFinderSpecification attributeFinderSpecification,
            AttributeFinder attributeFinder) {
        log.debug("Publishing PIP: {}", attributeFinderSpecification);
        val pipName        = attributeFinderSpecification.fullyQualifiedName();
        var findersForName = attributeFinderIndex.computeIfAbsent(pipName, k -> new ArrayList<>());
        findersForName.add(new SpecificationAndFinder(attributeFinderSpecification, attributeFinder));

        for (var invocationAndStreams : activeStreamIndex.entrySet()) {
            val invocation     = invocationAndStreams.getKey();
            val streams        = invocationAndStreams.getValue();
            val newFinderMatch = attributeFinderSpecification.matches(invocation);
            if (newFinderMatch == Match.EXACT_MATCH || (newFinderMatch == Match.VARARGS_MATCH
                    && !doesExactlyMatchingPipExist(findersForName, invocation))) {
                connectStreamsToPip(attributeFinder, streams);
            }
        }
    }

    private boolean doesExactlyMatchingPipExist(Iterable<SpecificationAndFinder> pipsForName,
            final AttributeFinderInvocation invocation) {
        for (var pip : pipsForName) {
            val existingPipMatch = pip.specification().matches(invocation);
            if (existingPipMatch == Match.EXACT_MATCH) {
                return true;
            }
        }
        return false;
    }

    private void connectStreamsToPip(AttributeFinder policyInformationPoint, final Iterable<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.connectToPolicyInformationPoint(policyInformationPoint);
        }
    }

    @Override
    public void unloadPolicyInformationPoint(String name) {
        synchronized (lock) {
            val pipToRemove = pipRegistry.get(name);
            if (null == pipToRemove) {
                return;
            }
            // Make sure varargs PIPs are removed first when unloading a class to
            // avoid race conditions so that this does not lead to a case where this
            // temporarily switches to a varargs PIP that is about to be removed.
            // This is the inverse order as at loading time.
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
        }
    }

    /**
     * Removes a PIP with a given specification from the broker and disconnects all
     * connected attribute streams.
     *
     * @param attributeFinderSpecification the specification of the PIP to remove
     */
    private void removeAttributeFinder(AttributeFinderSpecification attributeFinderSpecification) {
        log.debug("Unpublishing AttributeFinder: {}", attributeFinderSpecification);
        val attributeName = attributeFinderSpecification.fullyQualifiedName();
        val pipsForName   = attributeFinderIndex.get(attributeName);
        if (null == pipsForName) {
            return;
        }
        pipsForName.removeIf(pipAndSpec -> pipAndSpec.specification().equals(attributeFinderSpecification));
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
            // Check if the subscription now falls back to a still existing varargs PIP.
            // There cannot be another exact match, as the consistency rules at PIP load
            // time forbids it.
            if (removedPipMatch == Match.EXACT_MATCH) {
                for (var pip : pipsForName) {
                    if (pip.specification().matches(invocation) == Match.VARARGS_MATCH) {
                        connectStreamsToPip(pip.policyInformationPoint(), streams);
                    }
                }
            }
        }
    }

    private void disconnectStreams(final Iterable<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.disconnectFromPolicyInformationPoint();
        }
    }

}
