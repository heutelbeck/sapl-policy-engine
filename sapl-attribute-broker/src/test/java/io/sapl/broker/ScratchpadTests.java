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
package io.sapl.broker;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.broker.impl.AttributeStreamBroker;
import io.sapl.broker.impl.PolicyInformationPoint;
import io.sapl.broker.impl.PolicyInformationPointInvocation;
import io.sapl.broker.impl.PolicyInformationPointSpecification;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
class ScratchpadTests {

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

    @Test
    void foo() throws InterruptedException {
        var broker = new AttributeStreamBroker();

        var dummyPipSpec = new PolicyInformationPointSpecification("dummy.pip", true, 0, false, List.of(), List.of());
//        var dummyPip     = (PolicyInformationPoint) invocation -> Flux.range(0, 100)
//                .delayElements(Duration.ofSeconds(1L)).map(Val::of).log();
        var dummyPip = (PolicyInformationPoint) invocation -> Flux.range(0, 3).delayElements(Duration.ofSeconds(1L))
                .map(Val::of).log();

        broker.registerPolicyInformationPoint(dummyPipSpec, dummyPip);

        var invocation = new PolicyInformationPointInvocation("dummy.pip", null, List.of(), Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofMillis(50L), 20L);

        var attributeStream = broker.attributeStream(invocation);

        var streamSubscription = attributeStream.log().subscribe();

        Thread.sleep(18000L);

        streamSubscription.dispose();
        Thread.sleep(8000L);

    }
}
