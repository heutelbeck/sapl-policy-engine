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
package io.sapl.attributes;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("CachingAttributeBroker")
class CachingAttributeBrokerTests {

    @PolicyInformationPoint(name = "simple")
    static class SimplePIP {
        @Attribute
        public Flux<Value> attr1() {
            return Flux.just(Value.of("test"));
        }
    }

    @PolicyInformationPoint(name = "collision1")
    static class CollisionPIP1 {
        @Attribute(name = "attr")
        public Flux<Value> attr() {
            return Flux.just(Value.of("test1"));
        }
    }

    @PolicyInformationPoint(name = "collision2")
    static class CollisionPIP2 {
        @Attribute(name = "attr")
        public Flux<Value> attr() {
            return Flux.just(Value.of("test2"));
        }
    }

    @PolicyInformationPoint(name = "collision1param")
    static class CollisionParamPIP1 {
        @Attribute(name = "attr")
        public Flux<Value> attr(Value param) {
            return Flux.just(param);
        }
    }

    @PolicyInformationPoint(name = "collision2param")
    static class CollisionParamPIP2 {
        @Attribute(name = "attr")
        public Flux<Value> attr(Value param) {
            return Flux.just(param);
        }
    }

    @PolicyInformationPoint(name = "noncollision1")
    static class NonCollisionPIP1 {
        @Attribute(name = "attr1")
        public Flux<Value> attr1() {
            return Flux.just(Value.of("test1"));
        }
    }

    @PolicyInformationPoint(name = "noncollision2")
    static class NonCollisionPIP2 {
        @Attribute(name = "attr2")
        public Flux<Value> attr2() {
            return Flux.just(Value.of("test2"));
        }
    }

    @PolicyInformationPoint(name = "noncollisionparam1")
    static class NonCollisionParamPIP1 {
        @Attribute(name = "attr")
        public Flux<Value> attr() {
            return Flux.just(Value.of("test1"));
        }
    }

    @PolicyInformationPoint(name = "noncollisionparam2")
    static class NonCollisionParamPIP2 {
        @Attribute(name = "attr")
        public Flux<Value> attr(Value param) {
            return Flux.just(param);
        }
    }

    @PolicyInformationPoint(name = "multi")
    static class MultiAttributePIP {
        @Attribute
        public Flux<Value> attr1() {
            return Flux.just(Value.of("attr1"));
        }

        @Attribute
        public Flux<Value> attr2() {
            return Flux.just(Value.of("attr2"));
        }

        @Attribute
        public Flux<Value> attr3(Value param) {
            return Flux.just(param);
        }
    }

    static class NotAnnotated {
        @Attribute
        public Flux<Value> attr1() {
            return Flux.just(Value.of("test"));
        }
    }

    @PolicyInformationPoint(name = "")
    static class BlankName {
        @Attribute
        public Flux<Value> attr1() {
            return Flux.just(Value.of("test"));
        }
    }

    @PolicyInformationPoint(name = "noattrs")
    static class NoAttributes {
        public Flux<Value> notAnAttribute() {
            return Flux.just(Value.of("test"));
        }
    }

    @Test
    void whenLoadLibraryThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new SimplePIP());

        assertThat(broker.getLoadedLibraryNames()).contains("simple");
    }

    @Test
    void whenLoadMultipleLibrariesThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        broker.loadPolicyInformationPointLibrary(new HttpPolicyInformationPoint(mock(ReactiveWebClient.class)));

        assertThat(broker.getLoadedLibraryNames()).containsExactlyInAnyOrder("time", "http");
    }

    @Test
    void whenUnloadLibraryThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new SimplePIP());
        assertThat(broker.getLoadedLibraryNames()).contains("simple");

        val unloaded = broker.unloadPolicyInformationPointLibrary("simple");

        assertThat(unloaded).isTrue();
        assertThat(broker.getLoadedLibraryNames()).doesNotContain("simple");
    }

    @Test
    void whenUnloadNonExistentLibraryThenReturnsFalse() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        val unloaded = broker.unloadPolicyInformationPointLibrary("nonexistent");

        assertThat(unloaded).isFalse();
    }

    @Test
    void whenLoadLibraryWithoutAnnotationThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NotAnnotated()))
                .hasMessageContaining("must be annotated with @PolicyInformationPoint");
    }

    @Test
    void whenLoadLibraryWithBlankNameThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new BlankName()))
                .hasMessageContaining("name() cannot be blank");
    }

    @Test
    void whenLoadLibraryWithNoAttributesThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NoAttributes()))
                .hasMessageContaining("must have at least one @Attribute");
    }

    @Test
    void whenLoadDuplicateLibraryThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new SimplePIP());

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new SimplePIP()))
                .hasMessageContaining("Library already loaded: simple");
    }

    @Test
    void whenLoadPIPAttributesWithDifferentNamespacesThenNoCollision() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        // These PIPs have different namespaces (collision1 vs collision2)
        // so their attributes (collision1.attr vs collision2.attr) don't collide
        broker.loadPolicyInformationPointLibrary(new CollisionPIP1());
        broker.loadPolicyInformationPointLibrary(new CollisionPIP2());

        // Should succeed - no collision
        assertThat(broker.getLoadedLibraryNames()).containsExactlyInAnyOrder("collision1", "collision2");
    }

    @Test
    void whenLoadPIPWithNonCollidingAttributesDifferentNamesThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new NonCollisionPIP1());
        broker.loadPolicyInformationPointLibrary(new NonCollisionPIP2());

        assertThat(broker.getLoadedLibraryNames()).containsExactlyInAnyOrder("noncollision1", "noncollision2");
    }

    @Test
    void whenLoadPIPWithNonCollidingAttributesDifferentParameterCountThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new NonCollisionParamPIP1());
        broker.loadPolicyInformationPointLibrary(new NonCollisionParamPIP2());

        assertThat(broker.getLoadedLibraryNames()).containsExactlyInAnyOrder("noncollisionparam1",
                "noncollisionparam2");
    }

    @Test
    void whenLoadPIPWithMultipleAttributesThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new MultiAttributePIP());

        assertThat(broker.getLoadedLibraryNames()).contains("multi");
    }

    @Test
    void whenReloadLibraryAfterUnloadingThenSucceeds() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new SimplePIP());
        broker.unloadPolicyInformationPointLibrary("simple");
        broker.loadPolicyInformationPointLibrary(new SimplePIP());

        assertThat(broker.getLoadedLibraryNames()).contains("simple");
    }

    @Test
    void whenGetLoadedLibraryNamesInitiallyThenReturnsEmptySet() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        assertThat(broker.getLoadedLibraryNames()).isEmpty();
    }

}
