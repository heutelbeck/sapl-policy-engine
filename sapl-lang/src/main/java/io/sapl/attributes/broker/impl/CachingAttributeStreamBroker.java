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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeBrokerException;
import io.sapl.attributes.broker.api.AttributeFinder;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.attributes.broker.api.AttributeFinderSpecification;
import io.sapl.attributes.broker.api.AttributeFinderSpecification.Match;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class CachingAttributeStreamBroker implements AttributeStreamBroker {
    private static final String THE_SPECIFICATION_COLLISION_PIP_S_WITH_S_ERROR = "The specification of the new PIP:%s collides with an existing specification: %s.";
    static final Duration       DEFAULT_GRACE_PERIOD                           = Duration.ofMillis(3000L);

    private record SpecAndPip(AttributeFinderSpecification specification, AttributeFinder policyInformationPoint) {}

    private final Map<AttributeFinderInvocation, List<AttributeStream>> attributeStreamIndex = new HashMap<>();
    private final Map<String, List<SpecAndPip>>                         pipRegistry          = new HashMap<>();

    @Override
    public Flux<Val> attributeStream(AttributeFinderInvocation invocation) {
        final var attributeStreamReference = new AtomicReference<Flux<Val>>();
        attributeStreamIndex.compute(invocation, (attributeName, streams) -> {
            if (null == streams) {
                streams = new ArrayList<>();
            }
            AttributeStream stream;
            if (streams.isEmpty() || invocation.fresh()) {
                final var matchingSpecsAndPips = pipRegistry.get(invocation.attributeName());
                stream = newAttributeStream(invocation, matchingSpecsAndPips);
                streams.add(stream);
            } else {
                stream = streams.get(0);
            }
            attributeStreamReference.set(stream.getStream());
            return streams;
        });
        return attributeStreamReference.get();
    }

    /**
     * Create a new AttributeStream for an invocation.
     *
     * @param invocation an invocation
     * @param pipsWithNameOfInvocation all PIPs with the same name.
     * @return a new AttributeStream, which is connected to a matching PIP is
     * present. Else directly, an error is published in the stream that no PIP was
     * was found for the invocation.
     */
    private AttributeStream newAttributeStream(AttributeFinderInvocation invocation,
            List<SpecAndPip> pipsWithNameOfInvocation) {
        final var attributeStream             = new AttributeStream(invocation, this::removeAttributeStreamFromIndex,
                DEFAULT_GRACE_PERIOD);
        final var uniquePipMatchingInvocation = searchForMatchingPip(invocation, pipsWithNameOfInvocation);
        if (null == uniquePipMatchingInvocation) {
            attributeStream.publish(Val.error("No unique policy information point found for " + invocation));
        } else {
            attributeStream.connectToPolicyInformationPoint(uniquePipMatchingInvocation);
        }
        return attributeStream;
    }

    /**
     * Find a PIP with specification that matches an invocation in a list.
     *
     * @param invocation an invocation
     * @param pipsWithNameOfInvocation a List of PIPs with specification.
     * @return a PIP whose specification is matching the invocation, or null.
     */
    private AttributeFinder searchForMatchingPip(AttributeFinderInvocation invocation,
            List<SpecAndPip> pipsWithNameOfInvocation) {
        if (null == pipsWithNameOfInvocation) {
            return null;
        }
        AttributeFinder varArgsMatch = null;
        for (var specAndPip : pipsWithNameOfInvocation) {
            final var matchQuality = specAndPip.specification().matches(invocation);
            if (matchQuality == Match.EXACT_MATCH) {
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
        attributeStreamIndex.compute(attributeStream.getInvocation(), (i, streams) -> {
            streams.remove(attributeStream);
            return streams;
        });
    }

    /**
     * This method registers a new PIP with the attribute broker. The specification
     * must not collide with any existing specification. If there are any matching
     * attribute streams consumed by policies, the streams are connected to the new
     * PIP.
     *
     * @param pipSpecification The specification of the PIP.
     * @param policyInformationPoint The PIP itself.
     * @throws AttributeBrokerException if there is a specification collision.
     */
    public void registerAttributeFinder(AttributeFinderSpecification pipSpecification,
            AttributeFinder policyInformationPoint) {
        log.debug("Publishing PIP: {}", pipSpecification);
        pipRegistry.compute(pipSpecification.attributeName(), (key, pipsForName) -> {

            final var newPipsForName = new ArrayList<SpecAndPip>();
            if (null != pipsForName) {
                requireNoSpecCollision(pipsForName, pipSpecification);
                newPipsForName.addAll(pipsForName);
            }
            newPipsForName.add(new SpecAndPip(pipSpecification, policyInformationPoint));

            for (var invocationAndStreams : attributeStreamIndex.entrySet()) {
                final var invocation  = invocationAndStreams.getKey();
                final var streams     = invocationAndStreams.getValue();
                final var newPipMatch = pipSpecification.matches(invocation);

                if (newPipMatch == Match.EXACT_MATCH || (newPipMatch == Match.VARARGS_MATCH
                        && !doesExactlyMatchingPipExtist(pipsForName, invocation))) {
                    connectStreamsToPip(policyInformationPoint, streams);
                }
            }
            return newPipsForName;
        });
    }

    private boolean doesExactlyMatchingPipExtist(List<SpecAndPip> pipsForName,
            final AttributeFinderInvocation invocation) {
        for (var pip : pipsForName) {
            final var existingPipMatch = pip.specification().matches(invocation);
            if (existingPipMatch == Match.EXACT_MATCH) {
                return true;
            }
        }
        return false;
    }

    private void connectStreamsToPip(AttributeFinder policyInformationPoint, final List<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.connectToPolicyInformationPoint(policyInformationPoint);
        }
    }

    private void requireNoSpecCollision(List<SpecAndPip> specsAndPips, AttributeFinderSpecification pipSpecification) {
        for (var existingSpecAndPip : specsAndPips) {
            if (existingSpecAndPip.specification().collidesWith(pipSpecification)) {
                throw new AttributeBrokerException(String.format(THE_SPECIFICATION_COLLISION_PIP_S_WITH_S_ERROR,
                        existingSpecAndPip.specification(), pipSpecification));
            }
        }
    }

    /**
     * Removes a PIP with a given specification from the broker and disconnects all
     * connected attribute streams.
     *
     * @param pipSpecification the specification of the PIP to remove
     */
    public void removePolicyInformationPoint(AttributeFinderSpecification pipSpecification) {
        log.debug("Unpublishing PIP: {}", pipSpecification);
        pipRegistry.compute(pipSpecification.attributeName(), (key, pipsForName) -> {
            if (null == pipsForName) {
                return null;
            }
            final var newPipsForName = new ArrayList<SpecAndPip>();
            for (var pip : pipsForName) {
                if (!pip.specification().equals(pipSpecification)) {
                    newPipsForName.add(pip);
                }
            }

            for (var invocationAndStreams : attributeStreamIndex.entrySet()) {
                final var invocation      = invocationAndStreams.getKey();
                final var streams         = invocationAndStreams.getValue();
                final var removedPipMatch = pipSpecification.matches(invocation);

                if (removedPipMatch != Match.NO_MATCH) {
                    disconnectStreams(streams);
                    // Check if the subscription now falls back to a still existing var args PIP.
                    // There cannot be another exact match, as the consistency rules at PIP load
                    // time forbids it.

                    // TODO: Make sure varargs PIPs are removed first when unloading a class to
                    // avoid race conditions so that this does not lead to a case where this
                    // temporarily switches to a varargs PIP that is about to be removed.

                    if (removedPipMatch == Match.EXACT_MATCH) {
                        for (var pip : newPipsForName) {
                            if (pip.specification().matches(invocation) == Match.VARARGS_MATCH) {
                                connectStreamsToPip(pip.policyInformationPoint(), streams);
                            }
                        }
                    }

                }
            }
            return newPipsForName;
        });
    }

    private void disconnectStreams(final List<AttributeStream> streams) {
        for (var attributeStream : streams) {
            attributeStream.disconnectFromPolicyInformationPoint();
        }
    }

    @Override
    public List<String> providedFunctionsOfLibrary(String library) {
        return pipRegistry.keySet().stream().filter(s -> s.startsWith(library)).toList();
    }

    @Override
    public boolean isProvidedFunction(String fullyQualifiedFunctionName) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<String> getAllFullyQualifiedFunctions() {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public Map<String, JsonNode> getAttributeSchemas() {
        // TODO Auto-generated method stub
        return Map.of();
    }

    @Override
    public List<AttributeFinderSpecification> getAttributeMetatata() {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public List<String> getAvailableLibraries() {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public List<String> getEnvironmentAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public List<String> getAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public Map<String, String> getDocumentedAttributeCodeTemplates() {
        // TODO Auto-generated method stub
        return Map.of();
    }

    @Override
    public List<PolicyInformationPointDocumentation> getDocumentation() {
        // TODO Auto-generated method stub
        return List.of();
    }

}
