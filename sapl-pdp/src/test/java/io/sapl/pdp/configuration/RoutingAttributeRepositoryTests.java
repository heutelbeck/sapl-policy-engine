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
package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import lombok.val;

class RoutingAttributeRepositoryTests {
    @Test
    @DisplayName("An observer with an unknown configId triggers an error and quetes the observation")
    void whenObserveCalledForUnknownConfigThenErrorTriggeredAndQueued() {
        AttributeAccessContext context = new AttributeAccessContext(Value.ofObject(Map.of()), Value.ofObject(Map.of()),
                Value.ofObject(Map.of()));

        AttributeFinderInvocation invocation = new AttributeFinderInvocation("pdp-1", "unknown-config", "sapl.test",
                List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, false, context);

        try (var router = new RoutingAttributeRepository()) {
            List<Value> received     = new ArrayList<>();
            var         registration = router.observe(invocation, received::add);

            assertThat(received).hasSize(1);
            registration.close();
        }
    }

    @Test
    @DisplayName("canPrepare method accepts a configuration with an attributeRepository extension (fallback)")
    void whenExtensionIsMissingThenCanPrepareAccpets() {
        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isTrue();
        }
    }

    @Test
    @DisplayName("canPrepare method merges cleartext and secrets config into a valid combination")
    void whenExtensionIsSplitThenCanPrepareAccepts() {
        val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).build();

        val secrets = ObjectValue.builder().put("username", Value.of("sapl")).put("password", Value.of("secret"))
                .build();

        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of("attributeRepository", config), Map.of("attributeRepository", secrets),
                        Set.of());

        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isTrue();
        }
    }

    @Test
    @DisplayName("canPrepare method rejects an existing config that is invalid but existing for the attribute repository")
    void whenExtensionExistsButIsInvalidThenCanPrepareRejcts() {
        val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).build();

        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of("attributeRepository", config), Map.of(), Set.of());

        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isFalse();
        }
    }
}
