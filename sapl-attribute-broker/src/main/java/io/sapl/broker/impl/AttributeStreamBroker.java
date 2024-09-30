/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.broker.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.sapl.api.interpreter.Val;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class AttributeStreamBroker {
    static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMillis(3000L);

    private final Map<PolicyInformationPointInvocation, List<AttributeStream>> attributeStreamIndex = new ConcurrentHashMap<>();
    private final Map<String, List<SpecAndPip>>                                pipRegistry          = new ConcurrentHashMap<>();

    public Flux<Val> attributeStream(PolicyInformationPointInvocation invocation) {
        return attributeStream(invocation, false);
    }

    public Flux<Val> freshAttributeStream(PolicyInformationPointInvocation invocation) {
        return attributeStream(invocation, true);
    }

    private Flux<Val> attributeStream(PolicyInformationPointInvocation invocation, boolean fresh) {
        final var attributeStreamReference = new AtomicReference<AttributeStream>();

        /*
         * Design intent: To avoid deadlocks, the pipRegistry is always the first
         * locking data structure to be accessed before the attributeStreamIndex. If one
         * would call pipRegistry.get() within the newAttributeStream method, this could
         * potentially lead to a deadlock with the publishing and removal of PIPs to the
         * broker.
         */
        pipRegistry.compute(invocation.fullyQualifiedAttributeName(), (name, pipsWithNameOfInvocation) -> {
            attributeStreamIndex.compute(invocation, (attributeName, streams) -> {
                if (null == streams) {
                    streams = new ArrayList<>();
                }
                AttributeStream stream;
                if (streams.isEmpty() || fresh) {
                    stream = newAttributeStream(invocation, pipsWithNameOfInvocation);
                    streams.add(stream);
                } else {
                    stream = streams.get(0);
                }
                attributeStreamReference.set(stream);
                return streams;
            });
            return pipsWithNameOfInvocation;
        });

        final var attributeStream = attributeStreamReference.get();
        if (null == attributeStream) {
            return Flux.error(() -> new AttributeBrokerException(
                    String.format("Cannot create attribute stream for %s.", invocation)));
        }
        return attributeStream.getStream();
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
    private AttributeStream newAttributeStream(PolicyInformationPointInvocation invocation,
            List<SpecAndPip> pipsWithNameOfInvocation) {
        final var attributeStream             = new AttributeStream(invocation, this::removeAttributeStreamFromIndex,
                DEFAULT_GRACE_PERIOD);
        final var uniquePipMatchingInvocation = searchForMatchingPip(invocation, pipsWithNameOfInvocation);
        if (null == uniquePipMatchingInvocation) {
            attributeStream.publish(Val.error("No policy information point found for " + invocation));
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
    private PolicyInformationPoint searchForMatchingPip(PolicyInformationPointInvocation invocation,
            List<SpecAndPip> pipsWithNameOfInvocation) {
        if (null == pipsWithNameOfInvocation) {
            return null;
        }
        for (var apecAndPip : pipsWithNameOfInvocation) {
            if (apecAndPip.specification().matches(invocation)) {
                return apecAndPip.policyInformationPoint();
            }
        }
        return null;
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
     * attribute streams consumed my policies, the streams are connected to the new
     * PIP.
     *
     * @param pipSpecification The specification of the PIP.
     * @param policyInformationPoint The PIP itself.
     * @throws AttributeBrokerException if there is a specification collision.
     */
    public void registerPolicyInformationPoint(PolicyInformationPointSpecification pipSpecification,
            PolicyInformationPoint policyInformationPoint) {
        log.info("Publishing PIP: {}", pipSpecification);
        pipRegistry.compute(pipSpecification.fullyQualifiedAttributeName(), (key, pipsForName) -> {
            final var newPipsForName = new ArrayList<SpecAndPip>();
            if (null != pipsForName) {
                requireNoSpecCollision(pipsForName, pipSpecification);
                newPipsForName.addAll(pipsForName);
            }
            newPipsForName.add(new SpecAndPip(pipSpecification, policyInformationPoint));
            attributeStreamIndex.forEach((invocation, streams) -> {
                if (pipSpecification.matches(invocation)) {
                    for (var attributeStream : streams) {
                        attributeStream.connectToPolicyInformationPoint(policyInformationPoint);
                    }
                }
            });
            return newPipsForName;
        });
    }

    private void requireNoSpecCollision(List<SpecAndPip> specsAndPips,
            PolicyInformationPointSpecification pipSpecification) {
        for (var existingSpecAndPip : specsAndPips) {
            if (existingSpecAndPip.specification().collidesWith(pipSpecification)) {
                throw new AttributeBrokerException(String.format(
                        "The specification of the new PIP:%s collides with an existing specification: %s.",
                        existingSpecAndPip.specification(), pipSpecification));
            }
        }
    }

    /**
     * Removes a PIP with a given specification from the broker and disconnects all
     * connected attribute streams.
     *
     * @param pipSpecification the specification of the PIP to unpublish
     */
    public void removePolicyInformationPoint(PolicyInformationPointSpecification pipSpecification) {
        log.info("Unpublishing PIP: {}", pipSpecification);
        pipRegistry.compute(pipSpecification.fullyQualifiedAttributeName(), (key, pipsForName) -> {
            if (null == pipsForName) {
                return null;
            }
            final var newPipsForName = new ArrayList<SpecAndPip>();
            for (var pip : pipsForName) {
                if (!pip.specification().equals(pipSpecification)) {
                    newPipsForName.add(pip);
                }
            }
            attributeStreamIndex.forEach((invocation, streams) -> {
                if (pipSpecification.matches(invocation)) {
                    for (var attributeStream : streams) {
                        attributeStream.disconnectFromPolicyInformationPoint();
                    }
                }
            });
            return newPipsForName;
        });
    }

    private record SpecAndPip(PolicyInformationPointSpecification specification,
            PolicyInformationPoint policyInformationPoint) {}
}
