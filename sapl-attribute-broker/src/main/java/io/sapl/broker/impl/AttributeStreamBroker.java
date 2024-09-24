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
    static final Duration DEFAULT_GRACE_PERIOD   = Duration.ofMillis(3000L);

    private final Map<PolicyInformationPointInvocation, List<AttributeStream>> attributeStreamIndex = new ConcurrentHashMap<>();
    private final Map<String, List<SpecAndPip>>                                pipRegistry          = new ConcurrentHashMap<>();

    public Flux<Val> attributeStream(PolicyInformationPointInvocation invocation) {
        return attributeStream(invocation, false);
    }

    public Flux<Val> freshAttributeStream(PolicyInformationPointInvocation invocation) {
        return attributeStream(invocation, true);
    }

    public Flux<Val> attributeStream(PolicyInformationPointInvocation invocation, boolean fresh) {
        final var attributeStreamReference = new AtomicReference<AttributeStream>();
        attributeStreamIndex.compute(invocation, (attributeName, streams) -> {
            if (null == streams) {
                streams = new ArrayList<>();
            }
            AttributeStream stream;
            if (streams.isEmpty() || fresh) {
                stream = newAttributeStream(invocation);
                streams.add(stream);
            } else {
                stream = streams.get(0);
            }
            attributeStreamReference.set(stream);
            return streams;
        });
        // TODO: null check
        return attributeStreamReference.get().getStream();
    }

    private AttributeStream newAttributeStream(PolicyInformationPointInvocation invocation) {
        final var attributeStream             = new AttributeStream(invocation, this::removeAttributeStreamFromIndex,
                DEFAULT_GRACE_PERIOD);
        final var pipsWithNameOfInvocation    = pipRegistry.get(invocation.fullyQualifiedAttributeName());
        final var uniquePipMatchingInvocation = searchForMatchingPip(invocation, pipsWithNameOfInvocation);
        if (null == uniquePipMatchingInvocation) {
            attributeStream.publish(Val.error("No policy information point found for " + invocation));
        } else {
            attributeStream.connectTo(uniquePipMatchingInvocation);
        }
        return attributeStream;
    }

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

    private void removeAttributeStreamFromIndex(AttributeStream attributeStream) {
        attributeStreamIndex.compute(attributeStream.getInvocation(), (i, streams) -> {
            streams.remove(attributeStream);
            return streams;
        });
    }

    public void publishPolicyInformationPoint(PolicyInformationPointSpecification pipSpecification,
            PolicyInformationPoint policyInformationPoint) {
        log.info("Publishing PIP: {}", pipSpecification);
        pipRegistry.compute(pipSpecification.fullyQualifiedAttributeName(), (key, pipsForName) -> {
            final var newPipsForName = new ArrayList<SpecAndPip>();
            if (null != pipsForName) {
                requireNoSpecCollision(pipsForName, pipSpecification);
                newPipsForName.addAll(pipsForName);
            }
            newPipsForName.add(new SpecAndPip(pipSpecification, policyInformationPoint));
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

    public void unpublishPolicyInformationPoint(PolicyInformationPointSpecification pipSpecification) {
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
            return newPipsForName;
        });
    }

    private record SpecAndPip(PolicyInformationPointSpecification specification,
            PolicyInformationPoint policyInformationPoint) {
    }
}
