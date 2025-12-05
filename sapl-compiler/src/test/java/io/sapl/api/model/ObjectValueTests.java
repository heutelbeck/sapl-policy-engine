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
    @DisplayName("Construction")
    class ConstructionTests {
        @Test
        @DisplayName("Constructor with null map throws NullPointerException")
        void when_constructedWithNullMap_then_throwsNullPointerException() {
            assertThatThrownBy(() -> new ObjectValue(null, ValueMetadata.EMPTY))
                    .isInstanceOf(NullPointerException.class);
        }
    }

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
        @DisplayName("Empty secret builder returns new instance")
        void when_emptySecretBuilder_then_returnsNewInstance() {
            var result = ObjectValue.builder().secret().build();

            assertThat(result).isNotSameAs(Value.EMPTY_OBJECT).isEmpty();
            assertThat(result.isSecret()).isTrue();
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
        @DisplayName("Builder secret() marks as secret")
        void when_builderSecretCalled_then_marksAsSecret() {
            var result = ObjectValue.builder().put("key", Value.of("value")).secret().build();

            assertThat(result.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() called before put() marks subsequent entries as secret")
        void when_secretCalledBeforePut_then_marksEntriesAsSecret() {
            val grimoire = ObjectValue.builder().secret().put("ritual", Value.of("Summon Yog-Sothoth"))
                    .put("location", Value.of("Miskatonic University")).build();

            assertThat(grimoire.isSecret()).isTrue();
            val ritual = grimoire.get("ritual");
            assertThat(ritual).isNotNull();
            assertThat(ritual.isSecret()).isTrue();
            val location = grimoire.get("location");
            assertThat(location).isNotNull();
            assertThat(location.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() called after put() marks existing entries as secret")
        void when_secretCalledAfterPut_then_marksExistingEntriesAsSecret() {
            val forbiddenKnowledge = ObjectValue.builder().put("incantation", Value.of("Ph'nglui mglw'nafh"))
                    .put("deity", Value.of("Cthulhu")).secret().build();

            assertThat(forbiddenKnowledge.isSecret()).isTrue();
            val incantation = forbiddenKnowledge.get("incantation");
            assertThat(incantation).isNotNull();
            assertThat(incantation.isSecret()).isTrue();
            val deity = forbiddenKnowledge.get("deity");
            assertThat(deity).isNotNull();
            assertThat(deity.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder putAll() with secret builder marks all entries as secret")
        void when_putAllWithSecretBuilder_then_marksAllEntriesAsSecret() {
            val elderSigns = Map.<String, Value>of("pentagram", Value.of("Protective ward"), "eye",
                    Value.of("All-seeing symbol"), "star", Value.of("Gateway marker"));

            val secretGrimoire = ObjectValue.builder().secret().putAll(elderSigns).build();

            assertThat(secretGrimoire.isSecret()).isTrue();
            val pentagram = secretGrimoire.get("pentagram");
            assertThat(pentagram).isNotNull();
            assertThat(pentagram.isSecret()).isTrue();
            val eye = secretGrimoire.get("eye");
            assertThat(eye).isNotNull();
            assertThat(eye.isSecret()).isTrue();
            val star = secretGrimoire.get("star");
            assertThat(star).isNotNull();
            assertThat(star.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder putAll() then secret() marks all entries as secret")
        void when_putAllThenSecret_then_marksAllEntriesAsSecret() {
            val cultMembers = Map.<String, Value>of("highPriest", Value.of("Wilbur Whateley"), "acolyte",
                    Value.of("Lavinia Whateley"), "witness", Value.of("Henry Armitage"));

            val memberRegistry = ObjectValue.builder().putAll(cultMembers).secret().build();

            assertThat(memberRegistry.isSecret()).isTrue();
            val highPriest = memberRegistry.get("highPriest");
            assertThat(highPriest).isNotNull();
            assertThat(highPriest.isSecret()).isTrue();
            val acolyte = memberRegistry.get("acolyte");
            assertThat(acolyte).isNotNull();
            assertThat(acolyte.isSecret()).isTrue();
            val witness = memberRegistry.get("witness");
            assertThat(witness).isNotNull();
            assertThat(witness.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() is idempotent")
        void when_secretCalledMultipleTimes_then_isIdempotent() {
            val necronomicon = ObjectValue.builder().put("chapter", Value.of("The Dunwich Horror")).secret().secret()
                    .put("page", Value.of(731)).secret().build();

            assertThat(necronomicon.isSecret()).isTrue();
            val chapter = necronomicon.get("chapter");
            assertThat(chapter).isNotNull();
            assertThat(chapter.isSecret()).isTrue();
            val page = necronomicon.get("page");
            assertThat(page).isNotNull();
            assertThat(page.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Builder mixed operations maintain secret consistency")
        void when_mixedOperationsUsed_then_maintainsSecretConsistency() {
            val ritualComponents = Map.<String, Value>of("candles", Value.of(13), "incense", Value.of("sandalwood"));

            val ritual = ObjectValue.builder().put("tome", Value.of("De Vermis Mysteriis")).secret()
                    .putAll(ritualComponents).put("bloodOffering", Value.of("goat")).build();

            assertThat(ritual.isSecret()).isTrue();
            assertThat(ritual).hasSize(4);
            ritual.values().forEach(value -> assertThat(value.isSecret()).isTrue());
        }

        @Test
        @DisplayName("Builder with non-secret putAll preserves individual entry states")
        void when_nonSecretPutAll_then_preservesEntryStates() {
            val publicRecords = Map.<String, Value>of("town", Value.of("Arkham"), "population", Value.of(15000));

            val records = ObjectValue.builder().putAll(publicRecords).build();

            assertThat(records.isSecret()).isFalse();
            val town = records.get("town");
            assertThat(town).isNotNull();
            assertThat(town.isSecret()).isFalse();
            val population = records.get("population");
            assertThat(population).isNotNull();
            assertThat(population.isSecret()).isFalse();
        }

        @Test
        @DisplayName("Builder withMetadata() merges attribute trace")
        void when_withMetadataCalled_then_mergesAttributeTrace() {
            var invocation      = new io.sapl.api.attributes.AttributeFinderInvocation("test-config", "test.attr",
                    java.util.List.of(), java.util.Map.of(), java.time.Duration.ofSeconds(5),
                    java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(100), 3, false);
            var attributeRecord = new io.sapl.api.pdp.internal.AttributeRecord(invocation, Value.of("result"),
                    java.time.Instant.now(), null);
            var metadata        = ValueMetadata.ofAttribute(attributeRecord);

            var result = ObjectValue.builder().withMetadata(metadata).put("key", Value.of("value")).build();

            assertThat(result.metadata().attributeTrace()).containsExactly(attributeRecord);
            val retrievedKey = result.get("key");
            assertThat(retrievedKey).isNotNull();
            assertThat(retrievedKey.metadata().attributeTrace()).containsExactly(attributeRecord);
        }

        @Test
        @DisplayName("Builder withMetadata() throws after build()")
        void when_withMetadataCalledAfterBuild_then_throwsIllegalStateException() {
            var builder = ObjectValue.builder().put("key", Value.of("value"));
            builder.build();

            var metadata = ValueMetadata.SECRET_EMPTY;
            assertThatThrownBy(() -> builder.withMetadata(metadata)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder withMetadata() combines with value metadata")
        void when_withMetadataAndValueMetadata_then_bothMerged() {
            var invocation1      = new io.sapl.api.attributes.AttributeFinderInvocation("test-config", "test.attr1",
                    java.util.List.of(), java.util.Map.of(), java.time.Duration.ofSeconds(5),
                    java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(100), 3, false);
            var attributeRecord1 = new io.sapl.api.pdp.internal.AttributeRecord(invocation1, Value.of("result1"),
                    java.time.Instant.now(), null);
            var invocation2      = new io.sapl.api.attributes.AttributeFinderInvocation("test-config", "test.attr2",
                    java.util.List.of(), java.util.Map.of(), java.time.Duration.ofSeconds(5),
                    java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(100), 3, false);
            var attributeRecord2 = new io.sapl.api.pdp.internal.AttributeRecord(invocation2, Value.of("result2"),
                    java.time.Instant.now(), null);

            var containerMetadata = ValueMetadata.ofAttribute(attributeRecord1);
            var valueMetadata     = ValueMetadata.ofAttribute(attributeRecord2);
            var valueWithMeta     = Value.of("value").withMetadata(valueMetadata);

            var result = ObjectValue.builder().withMetadata(containerMetadata).put("key", valueWithMeta).build();

            assertThat(result.metadata().attributeTrace()).containsExactly(attributeRecord1, attributeRecord2);
        }

        @Test
        @DisplayName("Builder withMetadata() preserves secret flag")
        void when_withMetadataWithSecret_then_preservesSecretFlag() {
            var result = ObjectValue.builder().withMetadata(ValueMetadata.SECRET_EMPTY)
                    .put("key", Value.of("sensitive")).build();

            assertThat(result.isSecret()).isTrue();
            val retrievedKey = result.get("key");
            assertThat(retrievedKey).isNotNull();
            assertThat(retrievedKey.isSecret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Secret Flag")
    class SecretFlagTests {

        @Test
        @DisplayName("Non-secret object is not secret")
        void when_nonSecretObject_then_secretIsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj.isSecret()).isFalse();
        }

        @Test
        @DisplayName("Secret object is secret")
        void when_secretObject_then_secretIsTrue() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            assertThat(obj.isSecret()).isTrue();
        }

        @Test
        @DisplayName("asSecret() on non-secret returns secret copy")
        void when_asSecretOnNonSecret_then_returnsSecretCopy() {
            var original = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var secret   = original.asSecret();

            assertThat(secret).isInstanceOf(ObjectValue.class);
            assertThat(secret.isSecret()).isTrue();
            assertThat(original.isSecret()).isFalse();
        }

        @Test
        @DisplayName("asSecret() on secret returns same instance")
        void when_asSecretOnSecret_then_returnsSameInstance() {
            var original = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var secret   = original.asSecret();

            assertThat(secret).isSameAs(original);
        }

        @Test
        @DisplayName("get() on secret object returns secret value")
        void when_getOnSecretObject_then_returnsSecretValue() {
            var obj   = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var value = obj.get("key");

            assertThat(value).isNotNull();
            assertThat(value.isSecret()).isTrue();
        }

        @Test
        @DisplayName("values() on secret object returns secret values")
        void when_valuesOnSecretObject_then_returnsSecretValues() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            obj.values().forEach(v -> assertThat(v.isSecret()).isTrue());
        }

        @Test
        @DisplayName("entrySet() on secret object returns secret values")
        void when_entrySetOnSecretObject_then_returnsSecretValues() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            obj.forEach((key, value) -> assertThat(value.isSecret()).isTrue());
        }

        @Test
        @DisplayName("forEach() on secret object provides secret values")
        void when_forEachOnSecretObject_then_providesSecretValues() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            obj.forEach((k, v) -> assertThat(v.isSecret()).isTrue());
        }
    }

    @Nested
    @DisplayName("Error-as-Value Pattern")
    class ErrorAsValueTests {

        @Test
        @DisplayName("get(null) returns ErrorValue")
        void when_getNull_then_returnsErrorValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.get(null);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Object key cannot be null");
            }
        }

        @Test
        @DisplayName("get(non-String) returns ErrorValue")
        void when_getNonString_then_returnsErrorValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.get(123);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Invalid key type", "String", "Integer");
            }
        }

        @Test
        @DisplayName("get(absent key) returns null")
        void when_getAbsentKey_then_returnsNull() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.get("absent");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("get(present key) returns value")
        void when_getPresentKey_then_returnsValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.get("key");

            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("getOrDefault(null) returns ErrorValue")
        void when_getOrDefaultNull_then_returnsErrorValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.getOrDefault(null, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(non-String) returns ErrorValue")
        void when_getOrDefaultNonString_then_returnsErrorValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.getOrDefault(123, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(absent key) returns default")
        void when_getOrDefaultAbsentKey_then_returnsDefault() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.getOrDefault("absent", Value.of(99));

            assertThat(result).isEqualTo(Value.of(99));
        }

        @Test
        @DisplayName("getOrDefault(present key) returns value")
        void when_getOrDefaultPresentKey_then_returnsValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var result = obj.getOrDefault("key", Value.of(99));

            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("ErrorValue from secret object is secret")
        void when_errorFromSecretObject_then_errorIsSecret() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var result = obj.get(null);

            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(result.isSecret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Map Contract")
    class MapContractTests {

        @Test
        @DisplayName("equals() accepts HashMap with same content")
        void when_equalsComparedWithHashMap_then_acceptsIt() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, ValueMetadata.EMPTY);
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).isEqualTo(hashMap);
            assertThat(hashMap).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("equals() accepts Map.of() with same content")
        void when_equalsComparedWithMapOf_then_acceptsIt() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, ValueMetadata.EMPTY);

            assertThat(objectValue).isEqualTo(content);
            assertThat(content).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("hashCode() matches HashMap hashCode")
        void when_hashCodeComputed_then_matchesHashMap() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, ValueMetadata.EMPTY);
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).hasSameHashCodeAs(hashMap);
        }

        @Test
        @DisplayName("Can be used in HashSet with plain Maps")
        void when_usedInHashSet_then_worksWithPlainMaps() {
            var set = new HashSet<>();
            set.add(Map.of("key", Value.of(1)));

            var objectValue = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(set).contains(objectValue);
        }

        @Test
        @DisplayName("Can be used as HashMap key with plain Maps")
        void when_usedAsHashMapKey_then_worksWithPlainMaps() {
            var map = new HashMap<>();
            map.put(Map.of("key", Value.of(1)), "test");

            val objectValue = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(map).containsEntry(objectValue, "test");
        }

        @Test
        @DisplayName("containsKey() returns false for non-String key")
        void when_containsKeyWithNonString_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj.containsKey(123)).isFalse();
            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey() returns true for present String key")
        void when_containsKeyWithPresentKey_then_returnsTrue() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).containsKey("key");
        }

        @Test
        @DisplayName("containsKey() returns false for absent String key")
        void when_containsKeyWithAbsentKey_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).doesNotContainKey("absent");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("put() throws UnsupportedOperationException")
        void when_putCalled_then_throwsUnsupportedOperationException() {
            var obj      = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var newValue = Value.of(2);

            assertThatThrownBy(() -> obj.put("newKey", newValue)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("remove() throws UnsupportedOperationException")
        void when_removeCalled_then_throwsUnsupportedOperationException() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThatThrownBy(() -> obj.remove("key")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("clear() throws UnsupportedOperationException")
        void when_clearCalled_then_throwsUnsupportedOperationException() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThatThrownBy(obj::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("putAll() throws UnsupportedOperationException")
        void when_putAllCalled_then_throwsUnsupportedOperationException() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
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
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), ValueMetadata.EMPTY);

            assertThat(obj).hasSize(2);
        }

        @Test
        @DisplayName("isEmpty() returns true for empty object")
        void when_isEmptyOnEmpty_then_returnsTrue() {
            var obj = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(obj).isEmpty();
        }

        @Test
        @DisplayName("isEmpty() returns false for non-empty object")
        void when_isEmptyOnNonEmpty_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).isNotEmpty();
        }

        @Test
        @DisplayName("containsValue() returns true for present value")
        void when_containsValueForPresent_then_returnsTrue() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).containsValue(Value.of(1));
        }

        @Test
        @DisplayName("containsValue() returns false for absent value")
        void when_containsValueForAbsent_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj.containsValue(Value.of(2))).isFalse();
        }

        @Test
        @DisplayName("keySet() returns all keys")
        void when_keySetCalled_then_returnsAllKeys() {
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), ValueMetadata.EMPTY);

            assertThat(obj.keySet()).containsExactlyInAnyOrder("k1", "k2");
        }

        @Test
        @DisplayName("values() returns all values")
        void when_valuesCalled_then_returnsAllValues() {
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), ValueMetadata.EMPTY);

            assertThat(obj.values()).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
        }

        @Test
        @DisplayName("entrySet() returns all entries")
        void when_entrySetCalled_then_returnsAllEntries() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

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
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).isEqualTo(obj);
        }

        @Test
        @DisplayName("equals() is symmetric")
        void when_equalsChecked_then_isSymmetric() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
            assertThat(obj2).isEqualTo(obj1).hasSameHashCodeAs(obj1);
        }

        @Test
        @DisplayName("equals() is transitive")
        void when_equalsChecked_then_isTransitive() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var obj3 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
            assertThat(obj2).isEqualTo(obj3).hasSameHashCodeAs(obj3);
            assertThat(obj1).isEqualTo(obj3).hasSameHashCodeAs(obj3);
        }

        @Test
        @DisplayName("equals() ignores secret flag")
        void when_equalsChecked_then_ignoresSecretFlag() {
            var regular = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var secret  = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            assertThat(regular).isEqualTo(secret).hasSameHashCodeAs(secret);
            assertThat(secret).isEqualTo(regular).hasSameHashCodeAs(regular);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals()")
        void when_hashCodeComputed_then_consistentWithEquals() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
        }

        @Test
        @DisplayName("hashCode() ignores secret flag")
        void when_hashCodeComputed_then_ignoresSecretFlag() {
            var regular = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var secret  = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            assertThat(regular).hasSameHashCodeAs(secret);
        }

        @Test
        @DisplayName("equals() returns false for different content")
        void when_equalsComparedWithDifferentContent_then_returnsFalse() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);
            var obj2 = new ObjectValue(Map.of("key", Value.of(2)), ValueMetadata.EMPTY);

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for different keys")
        void when_equalsComparedWithDifferentKeys_then_returnsFalse() {
            var obj1 = new ObjectValue(Map.of("key1", Value.of(1)), ValueMetadata.EMPTY);
            var obj2 = new ObjectValue(Map.of("key2", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for null")
        void when_equalsComparedWithNull_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals() returns false for non-Map object")
        void when_equalsComparedWithNonMap_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj).isNotEqualTo("not a map");
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString() for non-secret shows content")
        void when_toStringOnNonSecret_then_showsContent() {
            var obj    = new ObjectValue(Map.of("name", Value.of("Alice")), ValueMetadata.EMPTY);
            var result = obj.toString();

            assertThat(result).contains("name", "Alice").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() for secret shows placeholder")
        void when_toStringOnSecret_then_showsPlaceholder() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);

            assertThat(obj).hasToString("***SECRET***");
        }

        @Test
        @DisplayName("toString() for empty non-secret shows {}")
        void when_toStringOnEmpty_then_showsBraces() {
            var obj = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(obj).hasToString("{}");
        }
    }

    @Nested
    @DisplayName("Builder - Enhanced Coverage")
    class BuilderEnhancedTests {

        @Test
        @DisplayName("Builder with elements and secret propagates secret to accessed elements")
        void when_builderWithSecretPropagates_then_accessedElementsAreSecret() {
            var obj = ObjectValue.builder().put("key1", Value.of(1)).put("key2", Value.of(2)).secret().build();

            assertThat(obj.isSecret()).isTrue();
            val key1 = obj.get("key1");
            assertThat(key1).isNotNull();
            assertThat(key1.isSecret()).isTrue();
            val key2 = obj.get("key2");
            assertThat(key2).isNotNull();
            assertThat(key2.isSecret()).isTrue();
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
            var builder = ObjectValue.builder().put("cultist", Value.of("Wilbur Whateley"));
            builder.build();

            val elderValue = Value.of("Yog-Sothoth");
            assertThatThrownBy(() -> builder.put("elder", elderValue)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on putAll after build()")
        void when_putAllCalledAfterBuild_then_throwsException() {
            var builder = ObjectValue.builder().put("tome", Value.of("Necronomicon"));
            builder.build();

            var moreEntries = Map.<String, Value>of("ritual", Value.of("Summoning"));
            assertThatThrownBy(() -> builder.putAll(moreEntries)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on secret after build()")
        void when_secretCalledAfterBuild_then_throwsException() {
            var builder = ObjectValue.builder().put("incantation", Value.of("Ph'nglui mglw'nafh"));
            builder.build();

            assertThatThrownBy(builder::secret).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on multiple build() calls")
        void when_buildCalledMultipleTimes_then_throwsException() {
            var builder = ObjectValue.builder().put("elder", Value.of("Cthulhu"));
            builder.build();

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder secret can be set before or after adding properties")
        void when_secretSetBeforeOrAfterProperties_then_bothWork() {
            var secretFirst = ObjectValue.builder().secret().put("key", Value.of(1)).build();
            var secretLast  = ObjectValue.builder().put("key", Value.of(1)).secret().build();

            assertThat(secretFirst.isSecret()).isTrue();
            assertThat(secretLast.isSecret()).isTrue();
            val firstKey = secretFirst.get("key");
            assertThat(firstKey).isNotNull();
            assertThat(firstKey.isSecret()).isTrue();
            val lastKey = secretLast.get("key");
            assertThat(lastKey).isNotNull();
            assertThat(lastKey.isSecret()).isTrue();
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
            var afterSecret = afterPut.secret();
            var afterPutAll = afterSecret.putAll(Map.of("k2", Value.of(2)));

            assertThat(afterPut).isSameAs(builder);
            assertThat(afterSecret).isSameAs(builder);
            assertThat(afterPutAll).isSameAs(builder);
        }

        @Test
        @DisplayName("Builder putAll with empty map works")
        void when_builderPutAllWithEmptyMap_then_works() {
            var result = ObjectValue.builder().putAll(Map.of()).build();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Builder with properties already marked as secret propagates secret to container")
        void when_builderWithSecretProperties_then_propagatesSecretToContainer() {
            var secretValue = Value.of(1).asSecret();
            // When a secret value is added, the container becomes secret too (metadata
            // aggregation)
            var objWithSecret = ObjectValue.builder().put("key", secretValue).build();

            // Container is secret because it contains a secret value
            assertThat(objWithSecret.isSecret()).isTrue();
            val retrievedKey = objWithSecret.get("key");
            assertThat(retrievedKey).isNotNull();
            assertThat(retrievedKey.isSecret()).isTrue();

            // Explicitly marking as secret on already-secret container doesn't change
            // anything
            var explicitlySecretObj = ObjectValue.builder().put("key", secretValue).secret().build();

            assertThat(explicitlySecretObj.isSecret()).isTrue();
            val secretKey = explicitlySecretObj.get("key");
            assertThat(secretKey).isNotNull();
            assertThat(secretKey.isSecret()).isTrue();
        }

        @Test
        @DisplayName("Secret flag semantics: additive with propagation")
        void when_secretFlagUsed_then_additiveWithPropagation() {
            var nonSecretValue = Value.of(1);
            var secretValue    = Value.of(2).asSecret();

            // Non-secret value in non-secret container stays non-secret
            var nonSecretContainer = ObjectValue.builder().put("key", nonSecretValue).build();
            assertThat(nonSecretContainer.isSecret()).isFalse();
            val value1 = nonSecretContainer.get("key");
            assertThat(value1).isNotNull();
            assertThat(value1.isSecret()).isFalse();

            // Secret value propagates to container (metadata aggregation)
            var containerWithSecret = ObjectValue.builder().put("key", secretValue).build();
            assertThat(containerWithSecret.isSecret()).isTrue();
            val value2 = containerWithSecret.get("key");
            assertThat(value2).isNotNull();
            assertThat(value2.isSecret()).isTrue();

            // Secret container propagates to non-secret value
            var secretContainer = ObjectValue.builder().put("key", nonSecretValue).secret().build();
            assertThat(secretContainer.isSecret()).isTrue();
            val value3 = secretContainer.get("key");
            assertThat(value3).isNotNull();
            assertThat(value3.isSecret()).isTrue();

            // Both secret container and secret value - all secret
            var allSecretContainer = ObjectValue.builder().put("key", secretValue).secret().build();
            assertThat(allSecretContainer.isSecret()).isTrue();
            val value4 = allSecretContainer.get("key");
            assertThat(value4).isNotNull();
            assertThat(value4.isSecret()).isTrue();
        }
    }

    @Nested
    @DisplayName("ErrorValue Secret Inheritance - CRITICAL")
    class ErrorValueSecretInheritanceTests {

        @Test
        @DisplayName("ErrorValue from get(null) inherits secret flag")
        void when_getWithNullOnSecret_then_errorInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var error  = secret.get(null);

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.isSecret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from get(non-String) inherits secret flag")
        void when_getWithNonStringOnSecret_then_errorInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var error  = secret.get(123);

            assertThat(error).isNotNull().isInstanceOf(ErrorValue.class);
            assertThat(error.isSecret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(null) inherits secret flag")
        void when_getOrDefaultWithNullOnSecret_then_errorInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var error  = secret.getOrDefault(null, Value.of(999));

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.isSecret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(non-String) inherits secret flag")
        void when_getOrDefaultWithNonStringOnSecret_then_errorInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var error  = secret.getOrDefault(456, Value.of(999));

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.isSecret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Secret Propagation - Additional Coverage")
    class SecretPropagationAdditionalTests {

        @Test
        @DisplayName("getOrDefault() propagates secret for found values")
        void when_getOrDefaultOnSecretWithFoundValue_then_propagatesSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var result = secret.getOrDefault("key", Value.of(999));

            assertThat(result).isNotNull();
            assertThat(result.isSecret()).isTrue();
        }

        @Test
        @DisplayName("getOrDefault() propagates secret for default values")
        void when_getOrDefaultOnSecretWithMissingKey_then_propagatesSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            var result = secret.getOrDefault("missing", Value.of(999));

            assertThat(result).isNotNull();
            assertThat(result.isSecret()).isTrue();
        }

        @Test
        @DisplayName("containsKey(null) returns false instead of throwing")
        void when_containsKeyWithNull_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey(non-String) returns false instead of throwing")
        void when_containsKeyWithNonString_then_returnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), ValueMetadata.EMPTY);

            assertThat(obj.containsKey(123)).isFalse();
        }
    }

    @Nested
    @DisplayName("ToString - Enhanced Coverage")
    class ToStringEnhancedTests {

        @Test
        @DisplayName("toString() with nested objects shows structure")
        void when_toStringWithNestedObjects_then_showsStructure() {
            var inner = new ObjectValue(Map.of("a", Value.of(1), "b", Value.of(2)), ValueMetadata.EMPTY);
            var outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), ValueMetadata.EMPTY);

            var result = outer.toString();

            assertThat(result).contains("inner", "c").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() with secret nested objects hides all content due to secret propagation")
        void when_toStringWithSecretNestedObjects_then_hidesAllContent() {
            var inner = new ObjectValue(Map.of("a", Value.of(1)), ValueMetadata.SECRET_EMPTY);
            // When outer contains a secret inner, the outer becomes secret too (metadata
            // aggregation)
            var outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), ValueMetadata.EMPTY);

            // The outer object is now secret because it contains secret content
            assertThat(outer.isSecret()).isTrue();
            var result = outer.toString();

            // Secret container hides all content
            assertThat(result).isEqualTo("***SECRET***");
        }

        @Test
        @DisplayName("toString() handles all value types")
        void when_toStringWithMixedTypes_then_handlesAllTypes() {
            var obj = new ObjectValue(Map.of("number", Value.of(1), "text", Value.of("hello"), "bool", Value.of(true),
                    "null", Value.NULL, "undefined", Value.UNDEFINED, "error", Value.error("test")),
                    ValueMetadata.EMPTY);

            var result = obj.toString();

            assertThat(result).contains("number").contains("text").contains("bool").contains("null")
                    .contains("undefined").contains("error");
        }

        @Test
        @DisplayName("toString() with many properties is readable")
        void when_toStringWithManyProperties_then_isReadable() {
            var builder = ObjectValue.builder();
            for (int i = 0; i < 10; i++) {
                builder.put("key" + i, Value.of(i));
            }
            var obj = builder.build();

            var result = obj.toString();

            assertThat(result).contains("key0").contains("key9");
        }
    }

    @Nested
    @DisplayName("Edge Cases - Additional Coverage")
    class EdgeCasesAdditionalTests {

        @Test
        @DisplayName("Empty keySet iteration")
        void when_iteratingEmptyKeySet_then_noElementsVisited() {
            var empty = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(empty.keySet()).isEmpty();
            for (String key : empty.keySet()) {
                fail("Should not iterate over empty keySet");
            }
        }

        @Test
        @DisplayName("Empty values iteration")
        void when_iteratingEmptyValues_then_noElementsVisited() {
            var empty = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(empty.values()).isEmpty();
            for (Value value : empty.values()) {
                fail("Should not iterate over empty values");
            }
        }

        @Test
        @DisplayName("Empty entrySet iteration")
        void when_iteratingEmptyEntrySet_then_noElementsVisited() {
            var empty = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(empty.entrySet()).isEmpty();
            for (var entry : empty.entrySet()) {
                fail("Should not iterate over empty entrySet");
            }
        }

        @Test
        @DisplayName("forEach on empty object")
        void when_forEachOnEmpty_then_noElementsVisited() {
            var empty   = new ObjectValue(Map.of(), ValueMetadata.EMPTY);
            var visited = new ArrayList<>();

            empty.forEach((k, v) -> visited.add(k));

            assertThat(visited).isEmpty();
        }

        @Test
        @DisplayName("get() on empty object returns null")
        void when_getOnEmpty_then_returnsNull() {
            var empty = new ObjectValue(Map.of(), ValueMetadata.EMPTY);

            assertThat(empty.get("anything")).isNull();
        }

        @Test
        @DisplayName("getOrDefault() on empty object returns default")
        void when_getOrDefaultOnEmpty_then_returnsDefault() {
            var empty        = new ObjectValue(Map.of(), ValueMetadata.EMPTY);
            var defaultValue = Value.of(999);

            var result = empty.getOrDefault("anything", defaultValue);

            assertThat(result).isEqualTo(defaultValue);
        }
    }

    @Nested
    @DisplayName("Pattern Matching Examples")
    class PatternMatchingTests {

        @Test
        @DisplayName("Pattern matching for resource access control")
        void when_patternMatchingForResourceAccess_then_grantsCorrectAccess() {
            var resourceData = Value.ofObject(Map.of("resourceId", Value.of("doc-123"), "owner", Value.of("alice")));

            var decision = switch (resourceData) {
            case ObjectValue obj when obj.get("owner") instanceof TextValue(String owner, ValueMetadata i) && "alice"
                    .equals(owner)                                                                                                  ->
                "Access granted to owner";
            case ObjectValue obj when obj.containsKey(
                    "resourceId")                                                                                                   ->
                "Read-only access";
            case ObjectValue ignored                                                                                                ->
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
