package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
class ScratchpadTests {

    @Test
    @Disabled
    void foo() throws InterruptedException {
        final var defaultTimeout = Duration.ofMillis(500L);
        final var attributeRepo  = new DummyAttributeRepository();
        final var registry       = new DummyPolicyInformationPointRegistry(attributeRepo);
        final var broker         = new AttributeStreamBroker(registry);

        final var invocation = new PolicyInformationPointInvocation("some.attribute", null, List.of(), Map.of());
        final var sub1       = broker.attributeStream(invocation, false, defaultTimeout)
                .subscribe(v -> log.info("SUB1:" + v));
        Thread.sleep(2000L);
        final var sub2 = broker.attributeStream(invocation, false, defaultTimeout)
                .subscribe(v -> log.info("SUB2:" + v));
        Thread.sleep(2000L);
        sub1.dispose();
        Thread.sleep(2000L);
        sub2.dispose();
        Thread.sleep(1000L);
        final var sub3 = broker.attributeStream(invocation, false, defaultTimeout)
                .subscribe(v -> log.info("SUB3:" + v));
        Thread.sleep(3000L);
        sub3.dispose();
        log.info("ALL DISPOSED");
        Thread.sleep(8000L);
    }

    public interface PolicyInformationPointRegistry {
        Flux<Val> lookupAttributeStream(PolicyInformationPointInvocation invocation);
    }

    @FunctionalInterface
    public interface PolicyInformationPoint {
        Flux<Val> invoce(PolicyInformationPointInvocation invocation);
    }

  
    @RequiredArgsConstructor
    public static class DummyPolicyInformationPointRegistry implements PolicyInformationPointRegistry {

        private final AttributeRepository attributeRepository;

        @Override
        public Flux<Val> lookupAttributeStream(PolicyInformationPointInvocation invocation) {
            return Flux.range(0, 100).delayElements(Duration.ofSeconds(1L)).map(Val::of).log();
        }

        public void loadPolicyInformationPoint(PolicyInformationPointSpecification invocationSpecification,
                PolicyInformationPoint policyInformationPoint) {

        }
    }

    public interface AttributeRepository {

        Flux<Val> entityAttributeStream(String fullyQualifiedAttributeName, JsonNode entity);

        Flux<Val> environmentAttributeStream(String fullyQualifiedAttributeName);

        void publishEntityAttributeValue(String fullyQualifiedAttributeName, JsonNode entityKey, Val value,
                Duration ttl);

        void publishEnvironemntAttributeValue(String fullyQualifiedAttributeName, Val value, Duration ttl);
    }

    public static class DummyAttributeRepository implements AttributeRepository {

        @Override
        public Flux<Val> entityAttributeStream(String fullyQualifiedAttributeName, JsonNode entity) {
            return Flux.empty();
        }

        @Override
        public Flux<Val> environmentAttributeStream(String fullyQualifiedAttributeName) {
            return Flux.empty();
        }

        @Override
        public void publishEntityAttributeValue(String fullyQualifiedAttributeName, JsonNode entityKey, Val value,
                Duration ttl) {

        }

        @Override
        public void publishEnvironemntAttributeValue(String fullyQualifiedAttributeName, Val value, Duration ttl) {

        }

    }

    @RequiredArgsConstructor
    public static class AttributeStreamBroker {
        static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMillis(3000L);

        private final PolicyInformationPointRegistry policyInformationPointRegistry;

        private final Map<PolicyInformationPointInvocation, ActiveAttributeStream> attributeMap = new ConcurrentHashMap<>();

        /*
         * Strategies: * Dedicated Fresh Connection * Reuse connection and cache
         *
         */
        public Flux<Val> attributeStream(PolicyInformationPointInvocation invocation, boolean fresh,
                Duration initialTimeOut) {
            if (fresh) {
                var freshAttribute = newAttributeForKey(invocation);
                attributeMap.putIfAbsent(invocation, freshAttribute);
                return TimeOutWrapper.wrap(freshAttribute.getAttributeStream(), initialTimeOut);
            }
            return TimeOutWrapper.wrap(attributeMap.compute(invocation, this::reuseOrNew).getAttributeStream(),
                    initialTimeOut);
        }

        private ActiveAttributeStream reuseOrNew(PolicyInformationPointInvocation invocation,
                ActiveAttributeStream attribute) {
            if (attribute == null) {
                return newAttributeForKey(invocation);
            }
            return attribute;
        }

        private ActiveAttributeStream newAttributeForKey(PolicyInformationPointInvocation invocation) {
            return new ActiveAttributeStream(invocation,
                    policyInformationPointRegistry.lookupAttributeStream(invocation),
                    this::cleanupAttributeFromIndexAfterUse, DEFAULT_GRACE_PERIOD);
        }

        private void cleanupAttributeFromIndexAfterUse(ActiveAttributeStream attribute) {
            attributeMap.remove(attribute.getInvocation(), attribute);
        }

    }
}
