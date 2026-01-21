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
package io.sapl.api.model;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ObjectValue Tests")
class ObjectValueTests {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Empty builder returns EMPTY_OBJECT singleton")
        void when_emptyBuilder_then_returnsEmptyObjectSingleton() {
            var result = ObjectValue.builder().build();

            assertThat(result).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("Builder put() chains fluently")
        void when_builderPutCalled_then_chainsFluently() {
            var result = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of(30)).build();

            assertThat(result).hasSize(2).containsEntry("name", Value.of("Alice")).containsEntry("age", Value.of(30));
        }

        @Test
        @DisplayName("Builder putAll() works")
        void when_builderPutAllCalled_then_addsAllEntries() {
            var map    = Map.<String, Value>of("key1", Value.of("value1"), "key2", Value.of("value2"));
            var result = ObjectValue.builder().putAll(map).build();

            assertThat(result).hasSize(2).containsEntry("key1", Value.of("value1"));
        }

        @Test
        @DisplayName("Builder cannot be reused after build()")
        void when_builderReusedAfterBuild_then_throwsException() {
            var builder = ObjectValue.builder();
            var first   = builder.put("k1", Value.of(1)).build();

            assertThat(first).hasSize(1);
            val secondValue = Value.of(2);
            assertThatThrownBy(() -> builder.put("k2", secondValue)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on put after build()")
        void when_putCalledAfterBuild_then_throwsException() {
            var builder = ObjectValue.builder().put("key", Value.of("value"));
            builder.build();

            val newValue = Value.of("new");
            assertThatThrownBy(() -> builder.put("newKey", newValue)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on putAll after build()")
        void when_putAllCalledAfterBuild_then_throwsException() {
            var builder = ObjectValue.builder().put("key", Value.of("value"));
            builder.build();

            var moreEntries = Map.<String, Value>of("key2", Value.of("value2"));
            assertThatThrownBy(() -> builder.putAll(moreEntries)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on multiple build() calls")
        void when_buildCalledMultipleTimes_then_throwsException() {
            var builder = ObjectValue.builder().put("key", Value.of("value"));
            builder.build();

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder with mixed types creates heterogeneous object")
        void when_builderWithMixedTypes_then_createsHeterogeneousObject() {
            var result = ObjectValue.builder().put("number", Value.of(1)).put("text", Value.of("hello"))
                    .put("bool", Value.of(true)).put("null", Value.NULL).build();

            assertThat(result).hasSize(4);
            assertThat(result.get("number")).isInstanceOf(NumberValue.class);
            assertThat(result.get("text")).isInstanceOf(TextValue.class);
            assertThat(result.get("bool")).isInstanceOf(BooleanValue.class);
            assertThat(result.get("null")).isInstanceOf(NullValue.class);
        }

        @Test
        @DisplayName("Builder chaining returns same instance")
        void when_builderChained_then_returnsSameInstance() {
            var builder     = ObjectValue.builder();
            var afterPut    = builder.put("k1", Value.of(1));
            var afterPutAll = afterPut.putAll(Map.of("k2", Value.of(2)));

            assertThat(afterPut).isSameAs(builder);
            assertThat(afterPutAll).isSameAs(builder);
        }

        @Test
        @DisplayName("Builder putAll with empty map works")
        void when_builderPutAllWithEmptyMap_then_works() {
            var result = ObjectValue.builder().putAll(Map.of()).build();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Error-as-Value Pattern")
    class ErrorAsValueTests {

        @Test
        @DisplayName("get(null) returns ErrorValue")
        void when_getNull_then_returnsErrorValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.get(null);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Object key cannot be null");
            }
        }

        @Test
        @SuppressWarnings("unlikely-arg-type")
        @DisplayName("get(non-String) returns ErrorValue")
        void when_getNonString_then_returnsErrorValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.get(123);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Invalid key type", "String", "Integer");
            }
        }

        @Test
        @DisplayName("get(absent key) returns null")
        void when_getAbsentKey_then_returnsNull() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.get("absent");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("get(present key) returns value")
        void when_getPresentKey_then_returnsValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.get("key");

            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("getOrDefault(null) returns ErrorValue")
        void when_getOrDefaultNull_then_returnsErrorValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.getOrDefault(null, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(non-String) returns ErrorValue")
        void when_getOrDefaultNonString_then_returnsErrorValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.getOrDefault(123, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(absent key) returns default")
        void when_getOrDefaultAbsentKey_then_returnsDefault() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.getOrDefault("absent", Value.of(99));

            assertThat(result).isEqualTo(Value.of(99));
        }

        @Test
        @DisplayName("getOrDefault(present key) returns value")
        void when_getOrDefaultPresentKey_then_returnsValue() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var result = obj.getOrDefault("key", Value.of(99));

            assertThat(result).isEqualTo(Value.of(1));
        }
    }

    @Nested
    @DisplayName("Map Contract")
    class MapContractTests {

        @Test
        @DisplayName("equals() accepts HashMap with same content")
        void when_equalsComparedWithHashMap_then_acceptsIt() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = ObjectValue.builder().put("key", Value.of(1)).build();
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).isEqualTo(hashMap);
            assertThat(hashMap).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("equals() accepts Map.of() with same content")
        void when_equalsComparedWithMapOf_then_acceptsIt() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(objectValue).isEqualTo(content);
            assertThat(content).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("hashCode() matches HashMap hashCode")
        void when_hashCodeComputed_then_matchesHashMap() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = ObjectValue.builder().put("key", Value.of(1)).build();
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).hasSameHashCodeAs(hashMap);
        }

        @Test
        @DisplayName("Can be used in HashSet with plain Maps")
        void when_usedInHashSet_then_worksWithPlainMaps() {
            var set = new HashSet<>();
            set.add(Map.of("key", Value.of(1)));

            var objectValue = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(set).contains(objectValue);
        }

        @Test
        @DisplayName("Can be used as HashMap key with plain Maps")
        void when_usedAsHashMapKey_then_worksWithPlainMaps() {
            var map = new HashMap<>();
            map.put(Map.of("key", Value.of(1)), "test");

            val objectValue = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(map).containsEntry(objectValue, "test");
        }

        @Test
        @SuppressWarnings("unlikely-arg-type")
        @DisplayName("containsKey() returns false for non-String key")
        void when_containsKeyWithNonString_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj.containsKey(123)).isFalse();
            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey() returns true for present String key")
        void when_containsKeyWithPresentKey_then_returnsTrue() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).containsKey("key");
        }

        @Test
        @DisplayName("containsKey() returns false for absent String key")
        void when_containsKeyWithAbsentKey_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).doesNotContainKey("absent");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("put() throws UnsupportedOperationException")
        void when_putCalled_then_throwsUnsupportedOperationException() {
            var obj      = ObjectValue.builder().put("key", Value.of(1)).build();
            var newValue = Value.of(2);

            assertThatThrownBy(() -> obj.put("newKey", newValue)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("remove() throws UnsupportedOperationException")
        void when_removeCalled_then_throwsUnsupportedOperationException() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThatThrownBy(() -> obj.remove("key")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("clear() throws UnsupportedOperationException")
        void when_clearCalled_then_throwsUnsupportedOperationException() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThatThrownBy(obj::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("putAll() throws UnsupportedOperationException")
        void when_putAllCalled_then_throwsUnsupportedOperationException() {
            var obj    = ObjectValue.builder().put("key", Value.of(1)).build();
            var newMap = Map.of("newKey", Value.of(2));

            assertThatThrownBy(() -> obj.putAll(newMap)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Map Operations")
    class MapOperationsTests {

        @Test
        @DisplayName("size() returns correct size")
        void when_sizeCalled_then_returnsCorrectSize() {
            var obj = ObjectValue.builder().put("k1", Value.of(1)).put("k2", Value.of(2)).build();

            assertThat(obj).hasSize(2);
        }

        @Test
        @DisplayName("isEmpty() returns true for empty object")
        void when_isEmptyOnEmpty_then_returnsTrue() {
            var obj = ObjectValue.builder().build();

            assertThat(obj).isEmpty();
        }

        @Test
        @DisplayName("isEmpty() returns false for non-empty object")
        void when_isEmptyOnNonEmpty_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).isNotEmpty();
        }

        @Test
        @DisplayName("containsValue() returns true for present value")
        void when_containsValueForPresent_then_returnsTrue() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).containsValue(Value.of(1));
        }

        @Test
        @DisplayName("containsValue() returns false for absent value")
        void when_containsValueForAbsent_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj.containsValue(Value.of(2))).isFalse();
        }

        @Test
        @DisplayName("keySet() returns all keys")
        void when_keySetCalled_then_returnsAllKeys() {
            var obj = ObjectValue.builder().put("k1", Value.of(1)).put("k2", Value.of(2)).build();

            assertThat(obj.keySet()).containsExactlyInAnyOrder("k1", "k2");
        }

        @Test
        @DisplayName("values() returns all values")
        void when_valuesCalled_then_returnsAllValues() {
            var obj = ObjectValue.builder().put("k1", Value.of(1)).put("k2", Value.of(2)).build();

            assertThat(obj.values()).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
        }

        @Test
        @DisplayName("entrySet() returns all entries")
        void when_entrySetCalled_then_returnsAllEntries() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj.entrySet()).hasSize(1);
            Map.Entry<String, Value> entry = obj.entrySet().iterator().next();
            assertThat(entry.getKey()).isEqualTo("key");
            assertThat(entry.getValue()).isEqualTo(Value.of(1));
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("equals() is reflexive")
        void when_equalsChecked_then_isReflexive() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).isEqualTo(obj);
        }

        @Test
        @DisplayName("equals() is symmetric")
        void when_equalsChecked_then_isSymmetric() {
            var obj1 = ObjectValue.builder().put("key", Value.of(1)).build();
            var obj2 = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
            assertThat(obj2).isEqualTo(obj1).hasSameHashCodeAs(obj1);
        }

        @Test
        @DisplayName("equals() is transitive")
        void when_equalsChecked_then_isTransitive() {
            var obj1 = ObjectValue.builder().put("key", Value.of(1)).build();
            var obj2 = ObjectValue.builder().put("key", Value.of(1)).build();
            var obj3 = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
            assertThat(obj2).isEqualTo(obj3).hasSameHashCodeAs(obj3);
            assertThat(obj1).isEqualTo(obj3).hasSameHashCodeAs(obj3);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals()")
        void when_hashCodeComputed_then_consistentWithEquals() {
            var obj1 = ObjectValue.builder().put("key", Value.of(1)).build();
            var obj2 = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
        }

        @Test
        @DisplayName("equals() returns false for different content")
        void when_equalsComparedWithDifferentContent_then_returnsFalse() {
            var obj1 = ObjectValue.builder().put("key", Value.of(1)).build();
            var obj2 = ObjectValue.builder().put("key", Value.of(2)).build();

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for different keys")
        void when_equalsComparedWithDifferentKeys_then_returnsFalse() {
            var obj1 = ObjectValue.builder().put("key1", Value.of(1)).build();
            var obj2 = ObjectValue.builder().put("key2", Value.of(1)).build();

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for null")
        void when_equalsComparedWithNull_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals() returns false for non-Map object")
        void when_equalsComparedWithNonMap_then_returnsFalse() {
            var obj = ObjectValue.builder().put("key", Value.of(1)).build();

            assertThat(obj).isNotEqualTo("not a map");
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString() shows content")
        void when_toString_then_showsContent() {
            var obj    = ObjectValue.builder().put("name", Value.of("Alice")).build();
            var result = obj.toString();

            assertThat(result).contains("name", "Alice").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() for empty shows {}")
        void when_toStringOnEmpty_then_showsBraces() {
            var obj = ObjectValue.builder().build();

            assertThat(obj).hasToString("{}");
        }

        @Test
        @DisplayName("toString() with nested objects shows structure")
        void when_toStringWithNestedObjects_then_showsStructure() {
            var inner = ObjectValue.builder().put("a", Value.of(1)).put("b", Value.of(2)).build();
            var outer = ObjectValue.builder().put("inner", inner).put("c", Value.of(3)).build();

            var result = outer.toString();

            assertThat(result).contains("inner", "c").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() handles all value types")
        void when_toStringWithMixedTypes_then_handlesAllTypes() {
            var obj = ObjectValue.builder().put("number", Value.of(1)).put("text", Value.of("hello"))
                    .put("bool", Value.of(true)).put("null", Value.NULL).put("undefined", Value.UNDEFINED)
                    .put("error", Value.error("test")).build();

            var result = obj.toString();

            assertThat(result).contains("number").contains("text").contains("bool").contains("null")
                    .contains("undefined").contains("error");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Empty keySet iteration")
        void when_iteratingEmptyKeySet_then_noElementsVisited() {
            var empty = ObjectValue.builder().build();

            assertThat(empty.keySet()).isEmpty();
            for (@SuppressWarnings("unused")
            String key : empty.keySet()) {
                fail("Should not iterate over empty keySet");
            }
        }

        @Test
        @DisplayName("Empty values iteration")
        void when_iteratingEmptyValues_then_noElementsVisited() {
            var empty = ObjectValue.builder().build();

            assertThat(empty.values()).isEmpty();
            for (@SuppressWarnings("unused")
            Value value : empty.values()) {
                fail("Should not iterate over empty values");
            }
        }

        @Test
        @DisplayName("Empty entrySet iteration")
        void when_iteratingEmptyEntrySet_then_noElementsVisited() {
            var empty = ObjectValue.builder().build();

            assertThat(empty.entrySet()).isEmpty();
            for (@SuppressWarnings("unused")
            var entry : empty.entrySet()) {
                fail("Should not iterate over empty entrySet");
            }
        }

        @Test
        @DisplayName("forEach on empty object")
        void when_forEachOnEmpty_then_noElementsVisited() {
            var empty   = ObjectValue.builder().build();
            var visited = new ArrayList<>();

            empty.forEach((k, v) -> visited.add(k));

            assertThat(visited).isEmpty();
        }

        @Test
        @DisplayName("get() on empty object returns null")
        void when_getOnEmpty_then_returnsNull() {
            var empty = ObjectValue.builder().build();

            assertThat(empty.get("anything")).isNull();
        }

        @Test
        @DisplayName("getOrDefault() on empty object returns default")
        void when_getOrDefaultOnEmpty_then_returnsDefault() {
            var empty        = ObjectValue.builder().build();
            var defaultValue = Value.of(999);

            var result = empty.getOrDefault("anything", defaultValue);

            assertThat(result).isEqualTo(defaultValue);
        }
    }

    @Nested
    @DisplayName("Pattern Matching")
    class PatternMatchingTests {

        @Test
        @DisplayName("Pattern matching for resource access control")
        void when_patternMatchingForResourceAccess_then_grantsCorrectAccess() {
            var resourceData = Value.ofObject(Map.of("resourceId", Value.of("doc-123"), "owner", Value.of("alice")));

            var decision = switch (resourceData) {
            case ObjectValue obj when obj.get("owner") instanceof TextValue(String owner) && "alice".equals(owner) ->
                "Access granted to owner";
            case ObjectValue obj when obj.containsKey("resourceId")                                                ->
                "Read-only access";
            case ObjectValue ignored                                                                               ->
                "No access";
            };

            assertThat(decision).isEqualTo("Access granted to owner");
        }

        @Test
        @DisplayName("Pattern matching with null handling")
        void when_patternMatchingWithNullHandling_then_detectsNull() {
            var userData = Value.ofObject(Map.of("username", Value.of("bob"), "email", Value.NULL));

            boolean hasEmail = switch (userData) {
            case ObjectValue obj -> {
                var email = obj.get("email");
                yield !(email instanceof NullValue);
            }
            };

            assertThat(hasEmail).isFalse();
        }
    }
}
