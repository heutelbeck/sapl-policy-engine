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
        void constructorNullMapThrows() {
            assertThatThrownBy(() -> new ObjectValue(null, false)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Empty builder returns EMPTY_OBJECT singleton")
        void emptyBuilderReturnsSingleton() {
            var result = ObjectValue.builder().build();

            assertThat(result).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("Empty secret builder returns new instance")
        void emptySecretBuilderReturnsNewInstance() {
            var result = ObjectValue.builder().secret().build();

            assertThat(result).isNotSameAs(Value.EMPTY_OBJECT).isEmpty();
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder put() chains fluently")
        void builderPutChains() {
            var result = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of(30)).build();

            assertThat(result).hasSize(2).containsEntry("name", Value.of("Alice")).containsEntry("age", Value.of(30));
        }

        @Test
        @DisplayName("Builder putAll() works")
        void builderPutAll() {
            var map    = Map.<String, Value>of("key1", Value.of("value1"), "key2", Value.of("value2"));
            var result = ObjectValue.builder().putAll(map).build();

            assertThat(result).hasSize(2).containsEntry("key1", Value.of("value1"));
        }

        @Test
        @DisplayName("Builder secret() marks as secret")
        void builderSecret() {
            var result = ObjectValue.builder().put("key", Value.of("value")).secret().build();

            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() called before put() marks subsequent entries as secret")
        void secretBeforePutMarksEntriesSecret() {
            val grimoire = ObjectValue.builder().secret().put("ritual", Value.of("Summon Yog-Sothoth"))
                    .put("location", Value.of("Miskatonic University")).build();

            assertThat(grimoire.secret()).isTrue();
            val ritual = grimoire.get("ritual");
            assertThat(ritual).isNotNull();
            assertThat(ritual.secret()).isTrue();
            val location = grimoire.get("location");
            assertThat(location).isNotNull();
            assertThat(location.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() called after put() marks existing entries as secret")
        void secretAfterPutMarksEntriesSecret() {
            val forbiddenKnowledge = ObjectValue.builder().put("incantation", Value.of("Ph'nglui mglw'nafh"))
                    .put("deity", Value.of("Cthulhu")).secret().build();

            assertThat(forbiddenKnowledge.secret()).isTrue();
            val incantation = forbiddenKnowledge.get("incantation");
            assertThat(incantation).isNotNull();
            assertThat(incantation.secret()).isTrue();
            val deity = forbiddenKnowledge.get("deity");
            assertThat(deity).isNotNull();
            assertThat(deity.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder putAll() with secret builder marks all entries as secret")
        void putAllWithSecretBuilderMarksSecret() {
            val elderSigns = Map.<String, Value>of("pentagram", Value.of("Protective ward"), "eye",
                    Value.of("All-seeing symbol"), "star", Value.of("Gateway marker"));

            val secretGrimoire = ObjectValue.builder().secret().putAll(elderSigns).build();

            assertThat(secretGrimoire.secret()).isTrue();
            val pentagram = secretGrimoire.get("pentagram");
            assertThat(pentagram).isNotNull();
            assertThat(pentagram.secret()).isTrue();
            val eye = secretGrimoire.get("eye");
            assertThat(eye).isNotNull();
            assertThat(eye.secret()).isTrue();
            val star = secretGrimoire.get("star");
            assertThat(star).isNotNull();
            assertThat(star.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder putAll() then secret() marks all entries as secret")
        void putAllThenSecretMarksSecret() {
            val cultMembers = Map.<String, Value>of("highPriest", Value.of("Wilbur Whateley"), "acolyte",
                    Value.of("Lavinia Whateley"), "witness", Value.of("Henry Armitage"));

            val memberRegistry = ObjectValue.builder().putAll(cultMembers).secret().build();

            assertThat(memberRegistry.secret()).isTrue();
            val highPriest = memberRegistry.get("highPriest");
            assertThat(highPriest).isNotNull();
            assertThat(highPriest.secret()).isTrue();
            val acolyte = memberRegistry.get("acolyte");
            assertThat(acolyte).isNotNull();
            assertThat(acolyte.secret()).isTrue();
            val witness = memberRegistry.get("witness");
            assertThat(witness).isNotNull();
            assertThat(witness.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder secret() is idempotent")
        void secretIdempotency() {
            val necronomicon = ObjectValue.builder().put("chapter", Value.of("The Dunwich Horror")).secret().secret()
                    .put("page", Value.of(731)).secret().build();

            assertThat(necronomicon.secret()).isTrue();
            val chapter = necronomicon.get("chapter");
            assertThat(chapter).isNotNull();
            assertThat(chapter.secret()).isTrue();
            val page = necronomicon.get("page");
            assertThat(page).isNotNull();
            assertThat(page.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder mixed operations maintain secret consistency")
        void mixedOperationsMaintainSecretConsistency() {
            val ritualComponents = Map.<String, Value>of("candles", Value.of(13), "incense", Value.of("sandalwood"));

            val ritual = ObjectValue.builder().put("tome", Value.of("De Vermis Mysteriis")).secret()
                    .putAll(ritualComponents).put("bloodOffering", Value.of("goat")).build();

            assertThat(ritual.secret()).isTrue();
            assertThat(ritual).hasSize(4);
            ritual.values().forEach(value -> assertThat(value.secret()).isTrue());
        }

        @Test
        @DisplayName("Builder with non-secret putAll preserves individual entry states")
        void nonSecretPutAllPreservesStates() {
            val publicRecords = Map.<String, Value>of("town", Value.of("Arkham"), "population", Value.of(15000));

            val records = ObjectValue.builder().putAll(publicRecords).build();

            assertThat(records.secret()).isFalse();
            val town = records.get("town");
            assertThat(town).isNotNull();
            assertThat(town.secret()).isFalse();
            val population = records.get("population");
            assertThat(population).isNotNull();
            assertThat(population.secret()).isFalse();
        }
    }

    @Nested
    @DisplayName("Secret Flag")
    class SecretFlagTests {

        @Test
        @DisplayName("Non-secret object is not secret")
        void nonSecretObject() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj.secret()).isFalse();
        }

        @Test
        @DisplayName("Secret object is secret")
        void secretObject() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), true);

            assertThat(obj.secret()).isTrue();
        }

        @Test
        @DisplayName("asSecret() on non-secret returns secret copy")
        void asSecretReturnsSecretCopy() {
            var original = new ObjectValue(Map.of("key", Value.of(1)), false);
            var secret   = original.asSecret();

            assertThat(secret).isInstanceOf(ObjectValue.class);
            assertThat(secret.secret()).isTrue();
            assertThat(original.secret()).isFalse();
        }

        @Test
        @DisplayName("asSecret() on secret returns same instance")
        void asSecretOnSecretReturnsSame() {
            var original = new ObjectValue(Map.of("key", Value.of(1)), true);
            var secret   = original.asSecret();

            assertThat(secret).isSameAs(original);
        }

        @Test
        @DisplayName("get() on secret object returns secret value")
        void getOnSecretObjectReturnsSecret() {
            var obj   = new ObjectValue(Map.of("key", Value.of(1)), true);
            var value = obj.get("key");

            assertThat(value).isNotNull();
            assertThat(value.secret()).isTrue();
        }

        @Test
        @DisplayName("values() on secret object returns secret values")
        void valuesOnSecretObjectReturnsSecret() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), true);

            obj.values().forEach(v -> assertThat(v.secret()).isTrue());
        }

        @Test
        @DisplayName("entrySet() on secret object returns secret values")
        void entrySetOnSecretObjectReturnsSecret() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), true);

            obj.forEach((key, value) -> assertThat(value.secret()).isTrue());
        }

        @Test
        @DisplayName("forEach() on secret object provides secret values")
        void forEachOnSecretObjectProvidesSecret() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), true);

            obj.forEach((k, v) -> assertThat(v.secret()).isTrue());
        }
    }

    @Nested
    @DisplayName("Error-as-Value Pattern")
    class ErrorAsValueTests {

        @Test
        @DisplayName("get(null) returns ErrorValue")
        void getNullReturnsError() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.get(null);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Object key cannot be null");
            }
        }

        @Test
        @DisplayName("get(non-String) returns ErrorValue")
        void getNonStringReturnsError() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.get(123);

            assertThat(result).isInstanceOf(ErrorValue.class);
            if (result instanceof ErrorValue error) {
                assertThat(error.message()).contains("Invalid key type", "String", "Integer");
            }
        }

        @Test
        @DisplayName("get(absent key) returns null")
        void getAbsentKeyReturnsNull() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.get("absent");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("get(present key) returns value")
        void getPresentKeyReturnsValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.get("key");

            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("getOrDefault(null) returns ErrorValue")
        void getOrDefaultNullReturnsError() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.getOrDefault(null, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(non-String) returns ErrorValue")
        void getOrDefaultNonStringReturnsError() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.getOrDefault(123, Value.of(99));

            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(absent key) returns default")
        void getOrDefaultAbsentKeyReturnsDefault() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.getOrDefault("absent", Value.of(99));

            assertThat(result).isEqualTo(Value.of(99));
        }

        @Test
        @DisplayName("getOrDefault(present key) returns value")
        void getOrDefaultPresentKeyReturnsValue() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var result = obj.getOrDefault("key", Value.of(99));

            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("ErrorValue from secret object is secret")
        void errorFromSecretObjectIsSecret() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), true);
            var result = obj.get(null);

            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(result.secret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Map Contract")
    class MapContractTests {

        @Test
        @DisplayName("equals() accepts HashMap with same content")
        void equalsAcceptsHashMap() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, false);
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).isEqualTo(hashMap);
            assertThat(hashMap).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("equals() accepts Map.of() with same content")
        void equalsAcceptsMapOf() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, false);

            assertThat(objectValue).isEqualTo(content);
            assertThat(content).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("hashCode() matches HashMap hashCode")
        void hashCodeMatchesHashMap() {
            var content     = Map.<String, Value>of("key", Value.of(1));
            var objectValue = new ObjectValue(content, false);
            var hashMap     = new HashMap<>(content);

            assertThat(objectValue).hasSameHashCodeAs(hashMap);
        }

        @Test
        @DisplayName("Can be used in HashSet with plain Maps")
        void canBeUsedInHashSet() {
            var set = new HashSet<>();
            set.add(Map.of("key", Value.of(1)));

            var objectValue = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(set).contains(objectValue);
        }

        @Test
        @DisplayName("Can be used as HashMap key with plain Maps")
        void canBeUsedAsHashMapKey() {
            var map = new HashMap<>();
            map.put(Map.of("key", Value.of(1)), "test");

            val objectValue = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(map).containsEntry(objectValue, "test");
        }

        @Test
        @DisplayName("containsKey() returns false for non-String key")
        void containsKeyForNonStringReturnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj.containsKey(123)).isFalse();
            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey() returns true for present String key")
        void containsKeyForPresentKey() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).containsKey("key");
        }

        @Test
        @DisplayName("containsKey() returns false for absent String key")
        void containsKeyForAbsentKey() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).doesNotContainKey("absent");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("put() throws UnsupportedOperationException")
        void putThrows() {
            var obj      = new ObjectValue(Map.of("key", Value.of(1)), false);
            var newValue = Value.of(2);

            assertThatThrownBy(() -> obj.put("newKey", newValue)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("remove() throws UnsupportedOperationException")
        void removeThrows() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThatThrownBy(() -> obj.remove("key")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("clear() throws UnsupportedOperationException")
        void clearThrows() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThatThrownBy(obj::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("putAll() throws UnsupportedOperationException")
        void putAllThrows() {
            var obj    = new ObjectValue(Map.of("key", Value.of(1)), false);
            var newMap = Map.of("newKey", Value.of(2));

            assertThatThrownBy(() -> obj.putAll(newMap)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Map Operations")
    class MapOperationsTests {

        @Test
        @DisplayName("size() returns correct size")
        void sizeReturnsCorrectSize() {
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);

            assertThat(obj).hasSize(2);
        }

        @Test
        @DisplayName("isEmpty() returns true for empty object")
        void isEmptyForEmpty() {
            var obj = new ObjectValue(Map.of(), false);

            assertThat(obj).isEmpty();
        }

        @Test
        @DisplayName("isEmpty() returns false for non-empty object")
        void isEmptyForNonEmpty() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).isNotEmpty();
        }

        @Test
        @DisplayName("containsValue() returns true for present value")
        void containsValueForPresent() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).containsValue(Value.of(1));
        }

        @Test
        @DisplayName("containsValue() returns false for absent value")
        void containsValueForAbsent() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj.containsValue(Value.of(2))).isFalse();
        }

        @Test
        @DisplayName("keySet() returns all keys")
        void keySetReturnsAllKeys() {
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);

            assertThat(obj.keySet()).containsExactlyInAnyOrder("k1", "k2");
        }

        @Test
        @DisplayName("values() returns all values")
        void valuesReturnsAllValues() {
            var obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);

            assertThat(obj.values()).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
        }

        @Test
        @DisplayName("entrySet() returns all entries")
        void entrySetReturnsAllEntries() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

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
        void equalsIsReflexive() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).isEqualTo(obj);
        }

        @Test
        @DisplayName("equals() is symmetric")
        void equalsIsSymmetric() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj1).isEqualTo(obj2);
            assertThat(obj2).isEqualTo(obj1);
        }

        @Test
        @DisplayName("equals() is transitive")
        void equalsIsTransitive() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);
            var obj3 = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj1).isEqualTo(obj2);
            assertThat(obj2).isEqualTo(obj3);
            assertThat(obj1).isEqualTo(obj3);
        }

        @Test
        @DisplayName("equals() ignores secret flag")
        void equalsIgnoresSecretFlag() {
            var regular = new ObjectValue(Map.of("key", Value.of(1)), false);
            var secret  = new ObjectValue(Map.of("key", Value.of(1)), true);

            assertThat(regular).isEqualTo(secret);
            assertThat(secret).isEqualTo(regular);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals()")
        void hashCodeConsistentWithEquals() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            var obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj1).isEqualTo(obj2).hasSameHashCodeAs(obj2);
        }

        @Test
        @DisplayName("hashCode() ignores secret flag")
        void hashCodeIgnoresSecretFlag() {
            var regular = new ObjectValue(Map.of("key", Value.of(1)), false);
            var secret  = new ObjectValue(Map.of("key", Value.of(1)), true);

            assertThat(regular).hasSameHashCodeAs(secret);
        }

        @Test
        @DisplayName("equals() returns false for different content")
        void equalsReturnsFalseForDifferentContent() {
            var obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            var obj2 = new ObjectValue(Map.of("key", Value.of(2)), false);

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for different keys")
        void equalsReturnsFalseForDifferentKeys() {
            var obj1 = new ObjectValue(Map.of("key1", Value.of(1)), false);
            var obj2 = new ObjectValue(Map.of("key2", Value.of(1)), false);

            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for null")
        void equalsReturnsFalseForNull() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals() returns false for non-Map object")
        void equalsReturnsFalseForNonMap() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj).isNotEqualTo("not a map");
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString() for non-secret shows content")
        void toStringNonSecretShowsContent() {
            var obj    = new ObjectValue(Map.of("name", Value.of("Alice")), false);
            var result = obj.toString();

            assertThat(result).contains("name", "Alice").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() for secret shows placeholder")
        void toStringSecretShowsPlaceholder() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), true);

            assertThat(obj).hasToString("***SECRET***");
        }

        @Test
        @DisplayName("toString() for empty non-secret shows {}")
        void toStringEmptyShowsBraces() {
            var obj = new ObjectValue(Map.of(), false);

            assertThat(obj).hasToString("{}");
        }
    }

    @Nested
    @DisplayName("Builder - Enhanced Coverage")
    class BuilderEnhancedTests {

        @Test
        @DisplayName("Builder with elements and secret propagates secret to accessed elements")
        void builderSecretPropagatesOnAccess() {
            var obj = ObjectValue.builder().put("key1", Value.of(1)).put("key2", Value.of(2)).secret().build();

            assertThat(obj.secret()).isTrue();
            val key1 = obj.get("key1");
            assertThat(key1).isNotNull();
            assertThat(key1.secret()).isTrue();
            val key2 = obj.get("key2");
            assertThat(key2).isNotNull();
            assertThat(key2.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder cannot be reused after build()")
        void builderCannotBeReused() {
            var builder = ObjectValue.builder();
            var first   = builder.put("k1", Value.of(1)).build();

            assertThat(first).hasSize(1);
            assertThatThrownBy(() -> builder.put("k2", Value.of(2))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on put after build()")
        void builderThrowsOnPutAfterBuild() {
            var builder = ObjectValue.builder().put("cultist", Value.of("Wilbur Whateley"));
            builder.build();

            assertThatThrownBy(() -> builder.put("elder", Value.of("Yog-Sothoth")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on putAll after build()")
        void builderThrowsOnPutAllAfterBuild() {
            var builder = ObjectValue.builder().put("tome", Value.of("Necronomicon"));
            builder.build();

            var moreEntries = Map.<String, Value>of("ritual", Value.of("Summoning"));
            assertThatThrownBy(() -> builder.putAll(moreEntries)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on secret after build()")
        void builderThrowsOnSecretAfterBuild() {
            var builder = ObjectValue.builder().put("incantation", Value.of("Ph'nglui mglw'nafh"));
            builder.build();

            assertThatThrownBy(builder::secret).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder throws on multiple build() calls")
        void builderThrowsOnMultipleBuildCalls() {
            var builder = ObjectValue.builder().put("elder", Value.of("Cthulhu"));
            builder.build();

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("Builder secret can be set before or after adding properties")
        void builderSecretOrdering() {
            var secretFirst = ObjectValue.builder().secret().put("key", Value.of(1)).build();
            var secretLast  = ObjectValue.builder().put("key", Value.of(1)).secret().build();

            assertThat(secretFirst.secret()).isTrue();
            assertThat(secretLast.secret()).isTrue();
            val firstKey = secretFirst.get("key");
            assertThat(firstKey).isNotNull();
            assertThat(firstKey.secret()).isTrue();
            val lastKey = secretLast.get("key");
            assertThat(lastKey).isNotNull();
            assertThat(lastKey.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder with mixed types creates heterogeneous object")
        void builderMixedTypes() {
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
        void builderChainingReturnsSameInstance() {
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
        void builderPutAllEmptyMap() {
            var result = ObjectValue.builder().putAll(Map.of()).build();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Builder with properties already marked as secret")
        void builderWithSecretProperties() {
            var secretValue  = Value.of(1).asSecret();
            var nonSecretObj = ObjectValue.builder().put("key", secretValue).build();

            assertThat(nonSecretObj.secret()).isFalse();
            val nonSecretKey = nonSecretObj.get("key");
            assertThat(nonSecretKey).isNotNull();
            assertThat(nonSecretKey.secret()).isTrue();

            var secretObj = ObjectValue.builder().put("key", secretValue).secret().build();

            assertThat(secretObj.secret()).isTrue();
            val secretKey = secretObj.get("key");
            assertThat(secretKey).isNotNull();
            assertThat(secretKey.secret()).isTrue();
        }

        @Test
        @DisplayName("Secret flag semantics: additive, not overriding")
        void secretFlagSemantics() {
            var nonSecretValue = Value.of(1);
            var secretValue    = Value.of(2).asSecret();

            var nonSecretContainer1 = ObjectValue.builder().put("key", nonSecretValue).build();
            val value1              = nonSecretContainer1.get("key");
            assertThat(value1).isNotNull();
            assertThat(value1.secret()).isFalse();

            var nonSecretContainer2 = ObjectValue.builder().put("key", secretValue).build();
            val value2              = nonSecretContainer2.get("key");
            assertThat(value2).isNotNull();
            assertThat(value2.secret()).isTrue();

            var secretContainer1 = ObjectValue.builder().put("key", nonSecretValue).secret().build();
            val value3           = secretContainer1.get("key");
            assertThat(value3).isNotNull();
            assertThat(value3.secret()).isTrue();

            var secretContainer2 = ObjectValue.builder().put("key", secretValue).secret().build();
            val value4           = secretContainer2.get("key");
            assertThat(value4).isNotNull();
            assertThat(value4.secret()).isTrue();
        }
    }

    @Nested
    @DisplayName("ErrorValue Secret Inheritance - CRITICAL")
    class ErrorValueSecretInheritanceTests {

        @Test
        @DisplayName("ErrorValue from get(null) inherits secret flag")
        void errorFromGetNullInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var error  = secret.get(null);

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from get(non-String) inherits secret flag")
        void errorFromGetNonStringInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var error  = secret.get(123);

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(null) inherits secret flag")
        void errorFromGetOrDefaultNullInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var error  = secret.getOrDefault(null, Value.of(999));

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(non-String) inherits secret flag")
        void errorFromGetOrDefaultNonStringInheritsSecret() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var error  = secret.getOrDefault(456, Value.of(999));

            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Secret Propagation - Additional Coverage")
    class SecretPropagationAdditionalTests {

        @Test
        @DisplayName("getOrDefault() propagates secret for found values")
        void getOrDefaultPropagatesSecretForFound() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var result = secret.getOrDefault("key", Value.of(999));

            assertThat(result).isNotNull();
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("getOrDefault() propagates secret for default values")
        void getOrDefaultPropagatesSecretForDefault() {
            var secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            var result = secret.getOrDefault("missing", Value.of(999));

            assertThat(result).isNotNull();
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("containsKey(null) returns false instead of throwing")
        void containsKeyNullReturnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey(non-String) returns false instead of throwing")
        void containsKeyNonStringReturnsFalse() {
            var obj = new ObjectValue(Map.of("key", Value.of(1)), false);

            assertThat(obj.containsKey(123)).isFalse();
        }
    }

    @Nested
    @DisplayName("ToString - Enhanced Coverage")
    class ToStringEnhancedTests {

        @Test
        @DisplayName("toString() with nested objects shows structure")
        void toStringNestedObjects() {
            var inner = new ObjectValue(Map.of("a", Value.of(1), "b", Value.of(2)), false);
            var outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), false);

            var result = outer.toString();

            assertThat(result).contains("inner", "c").startsWith("{").endsWith("}");
        }

        @Test
        @DisplayName("toString() with secret nested objects hides inner content")
        void toStringSecretNestedObjects() {
            var inner = new ObjectValue(Map.of("a", Value.of(1)), true);
            var outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), false);

            var result = outer.toString();

            assertThat(result).contains("***SECRET***").contains("c");
        }

        @Test
        @DisplayName("toString() handles all value types")
        void toStringMixedTypes() {
            var obj = new ObjectValue(Map.of("number", Value.of(1), "text", Value.of("hello"), "bool", Value.of(true),
                    "null", Value.NULL, "undefined", Value.UNDEFINED, "error", Value.error("test")), false);

            var result = obj.toString();

            assertThat(result).contains("number").contains("text").contains("bool").contains("null")
                    .contains("undefined").contains("error");
        }

        @Test
        @DisplayName("toString() with many properties is readable")
        void toStringManyProperties() {
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
        void emptyKeySetIteration() {
            var empty = new ObjectValue(Map.of(), false);

            assertThat(empty.keySet()).isEmpty();
            for (String key : empty.keySet()) {
                fail("Should not iterate over empty keySet");
            }
        }

        @Test
        @DisplayName("Empty values iteration")
        void emptyValuesIteration() {
            var empty = new ObjectValue(Map.of(), false);

            assertThat(empty.values()).isEmpty();
            for (Value value : empty.values()) {
                fail("Should not iterate over empty values");
            }
        }

        @Test
        @DisplayName("Empty entrySet iteration")
        void emptyEntrySetIteration() {
            var empty = new ObjectValue(Map.of(), false);

            assertThat(empty.entrySet()).isEmpty();
            for (var entry : empty.entrySet()) {
                fail("Should not iterate over empty entrySet");
            }
        }

        @Test
        @DisplayName("forEach on empty object")
        void forEachOnEmpty() {
            var empty   = new ObjectValue(Map.of(), false);
            var visited = new ArrayList<>();

            empty.forEach((k, v) -> visited.add(k));

            assertThat(visited).isEmpty();
        }

        @Test
        @DisplayName("get() on empty object returns null")
        void getOnEmptyReturnsNull() {
            var empty = new ObjectValue(Map.of(), false);

            assertThat(empty.get("anything")).isNull();
        }

        @Test
        @DisplayName("getOrDefault() on empty object returns default")
        void getOrDefaultOnEmptyReturnsDefault() {
            var empty        = new ObjectValue(Map.of(), false);
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
        void patternMatchingResourceAccess() {
            var resourceData = Value.ofObject(Map.of("resourceId", Value.of("doc-123"), "owner", Value.of("alice")));

            var decision = switch (resourceData) {
            case ObjectValue obj when obj.get("owner") instanceof TextValue(String owner, boolean i) && "alice"
                    .equals(owner)                                                                                            ->
                "Access granted to owner";
            case ObjectValue obj when obj.containsKey(
                    "resourceId")                                                                                             ->
                "Read-only access";
            case ObjectValue ignored                                                                                          ->
                "No access";
            };

            assertThat(decision).isEqualTo("Access granted to owner");
        }

        @Test
        @DisplayName("Pattern matching with null handling")
        void patternMatchingNullHandling() {
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
