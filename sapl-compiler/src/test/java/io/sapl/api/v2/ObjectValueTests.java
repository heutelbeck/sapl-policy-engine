package io.sapl.api.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ObjectValue Tests")
class ObjectValueTests {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Constructor copies defensively")
        void constructorCopiesDefensively() {
            Map<String, Value> original = new HashMap<>(Map.of("key", Value.of(1)));
            ObjectValue obj = new ObjectValue(original, false);
            
            original.put("newKey", Value.of(2));
            
            assertThat(obj).hasSize(1);
        }

        @Test
        @DisplayName("Constructor with null map throws NullPointerException")
        void constructorNullMapThrows() {
            assertThatThrownBy(() -> new ObjectValue(null, false))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Empty builder returns EMPTY_OBJECT singleton")
        void emptyBuilderReturnsSingleton() {
            ObjectValue result = ObjectValue.builder().build();
            
            assertThat(result).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("Empty secret builder returns new instance")
        void emptySecretBuilderReturnsNewInstance() {
            ObjectValue result = ObjectValue.builder().secret().build();
            
            assertThat(result).isNotSameAs(Value.EMPTY_OBJECT);
            assertThat(result).isEmpty();
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("Builder put() chains fluently")
        void builderPutChains() {
            ObjectValue result = ObjectValue.builder()
                .put("name", Value.of("Alice"))
                .put("age", Value.of(30))
                .build();
            
            assertThat(result).hasSize(2);
            assertThat(result.get("name")).isEqualTo(Value.of("Alice"));
            assertThat(result.get("age")).isEqualTo(Value.of(30));
        }

        @Test
        @DisplayName("Builder putAll() works")
        void builderPutAll() {
            Map<String, Value> map = Map.of(
                "key1", Value.of("value1"),
                "key2", Value.of("value2")
            );
            ObjectValue result = ObjectValue.builder()
                .putAll(map)
                .build();
            
            assertThat(result).hasSize(2);
            assertThat(result.get("key1")).isEqualTo(Value.of("value1"));
        }

        @Test
        @DisplayName("Builder secret() marks as secret")
        void builderSecret() {
            ObjectValue result = ObjectValue.builder()
                .put("key", Value.of("value"))
                .secret()
                .build();
            
            assertThat(result.secret()).isTrue();
        }
    }

    @Nested
    @DisplayName("Secret Flag")
    class SecretFlagTests {

        @Test
        @DisplayName("Non-secret object is not secret")
        void nonSecretObject() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.secret()).isFalse();
        }

        @Test
        @DisplayName("Secret object is secret")
        void secretObject() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            assertThat(obj.secret()).isTrue();
        }

        @Test
        @DisplayName("asSecret() on non-secret returns secret copy")
        void asSecretReturnsSecretCopy() {
            ObjectValue original = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value secret = original.asSecret();
            
            assertThat(secret).isInstanceOf(ObjectValue.class);
            assertThat(secret.secret()).isTrue();
            assertThat(original.secret()).isFalse();
        }

        @Test
        @DisplayName("asSecret() on secret returns same instance")
        void asSecretOnSecretReturnsSame() {
            ObjectValue original = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value secret = original.asSecret();
            
            assertThat(secret).isSameAs(original);
        }

        @Test
        @DisplayName("get() on secret object returns secret value")
        void getOnSecretObjectReturnsSecret() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value value = obj.get("key");
            
            assertThat(value.secret()).isTrue();
        }

        @Test
        @DisplayName("values() on secret object returns secret values")
        void valuesOnSecretObjectReturnsSecret() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            obj.values().forEach(v -> assertThat(v.secret()).isTrue());
        }

        @Test
        @DisplayName("entrySet() on secret object returns secret values")
        void entrySetOnSecretObjectReturnsSecret() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            obj.entrySet().forEach(entry -> assertThat(entry.getValue().secret()).isTrue());
        }

        @Test
        @DisplayName("forEach() on secret object provides secret values")
        void forEachOnSecretObjectProvidesSecret() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            obj.forEach((k, v) -> assertThat(v.secret()).isTrue());
        }
    }

    @Nested
    @DisplayName("Error-as-Value Pattern")
    class ErrorAsValueTests {

        @Test
        @DisplayName("get(null) returns ErrorValue")
        void getNullReturnsError() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.get(null);
            
            assertThat(result).isInstanceOf(ErrorValue.class);
            ErrorValue error = (ErrorValue) result;
            assertThat(error.message()).contains("Object key cannot be null");
        }

        @Test
        @DisplayName("get(non-String) returns ErrorValue")
        void getNonStringReturnsError() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.get(123);
            
            assertThat(result).isInstanceOf(ErrorValue.class);
            ErrorValue error = (ErrorValue) result;
            assertThat(error.message()).contains("Invalid key type", "String", "Integer");
        }

        @Test
        @DisplayName("get(absent key) returns null")
        void getAbsentKeyReturnsNull() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.get("absent");
            
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("get(present key) returns value")
        void getPresentKeyReturnsValue() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.get("key");
            
            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("getOrDefault(null) returns ErrorValue")
        void getOrDefaultNullReturnsError() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.getOrDefault(null, Value.of(99));
            
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(non-String) returns ErrorValue")
        void getOrDefaultNonStringReturnsError() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.getOrDefault(123, Value.of(99));
            
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("getOrDefault(absent key) returns default")
        void getOrDefaultAbsentKeyReturnsDefault() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.getOrDefault("absent", Value.of(99));
            
            assertThat(result).isEqualTo(Value.of(99));
        }

        @Test
        @DisplayName("getOrDefault(present key) returns value")
        void getOrDefaultPresentKeyReturnsValue() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            Value result = obj.getOrDefault("key", Value.of(99));
            
            assertThat(result).isEqualTo(Value.of(1));
        }

        @Test
        @DisplayName("ErrorValue from secret object is secret")
        void errorFromSecretObjectIsSecret() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value result = obj.get(null);
            
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
            Map<String, Value> content = Map.of("key", Value.of(1));
            ObjectValue objectValue = new ObjectValue(content, false);
            HashMap<String, Value> hashMap = new HashMap<>(content);
            
            assertThat(objectValue).isEqualTo(hashMap);
            assertThat(hashMap).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("equals() accepts Map.of() with same content")
        void equalsAcceptsMapOf() {
            Map<String, Value> content = Map.of("key", Value.of(1));
            ObjectValue objectValue = new ObjectValue(content, false);
            
            assertThat(objectValue).isEqualTo(content);
            assertThat(content).isEqualTo(objectValue);
        }

        @Test
        @DisplayName("hashCode() matches HashMap hashCode")
        void hashCodeMatchesHashMap() {
            Map<String, Value> content = Map.of("key", Value.of(1));
            ObjectValue objectValue = new ObjectValue(content, false);
            HashMap<String, Value> hashMap = new HashMap<>(content);
            
            assertThat(objectValue.hashCode()).isEqualTo(hashMap.hashCode());
        }

        @Test
        @DisplayName("Can be used in HashSet with plain Maps")
        void canBeUsedInHashSet() {
            Set<Map<String, Value>> set = new HashSet<>();
            set.add(Map.of("key", Value.of(1)));
            
            ObjectValue objectValue = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(set.contains(objectValue)).isTrue();
        }

        @Test
        @DisplayName("Can be used as HashMap key with plain Maps")
        void canBeUsedAsHashMapKey() {
            Map<Map<String, Value>, String> map = new HashMap<>();
            map.put(Map.of("key", Value.of(1)), "test");
            
            ObjectValue objectValue = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(map.get(objectValue)).isEqualTo("test");
        }

        @Test
        @DisplayName("containsKey() returns false for non-String key")
        void containsKeyForNonStringReturnsFalse() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsKey(123)).isFalse();
            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey() returns true for present String key")
        void containsKeyForPresentKey() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsKey("key")).isTrue();
        }

        @Test
        @DisplayName("containsKey() returns false for absent String key")
        void containsKeyForAbsentKey() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsKey("absent")).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("put() throws UnsupportedOperationException")
        void putThrows() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThatThrownBy(() -> obj.put("newKey", Value.of(2)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("remove() throws UnsupportedOperationException")
        void removeThrows() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThatThrownBy(() -> obj.remove("key"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("clear() throws UnsupportedOperationException")
        void clearThrows() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThatThrownBy(() -> obj.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("putAll() throws UnsupportedOperationException")
        void putAllThrows() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThatThrownBy(() -> obj.putAll(Map.of("newKey", Value.of(2))))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Map Operations")
    class MapOperationsTests {

        @Test
        @DisplayName("size() returns correct size")
        void sizeReturnsCorrectSize() {
            ObjectValue obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);
            
            assertThat(obj.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("isEmpty() returns true for empty object")
        void isEmptyForEmpty() {
            ObjectValue obj = new ObjectValue(Map.of(), false);
            
            assertThat(obj.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty() returns false for non-empty object")
        void isEmptyForNonEmpty() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("containsValue() returns true for present value")
        void containsValueForPresent() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsValue(Value.of(1))).isTrue();
        }

        @Test
        @DisplayName("containsValue() returns false for absent value")
        void containsValueForAbsent() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsValue(Value.of(2))).isFalse();
        }

        @Test
        @DisplayName("keySet() returns all keys")
        void keySetReturnsAllKeys() {
            ObjectValue obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);
            
            assertThat(obj.keySet()).containsExactlyInAnyOrder("k1", "k2");
        }

        @Test
        @DisplayName("values() returns all values")
        void valuesReturnsAllValues() {
            ObjectValue obj = new ObjectValue(Map.of("k1", Value.of(1), "k2", Value.of(2)), false);
            
            assertThat(obj.values()).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
        }

        @Test
        @DisplayName("entrySet() returns all entries")
        void entrySetReturnsAllEntries() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
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
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj).isEqualTo(obj);
        }

        @Test
        @DisplayName("equals() is symmetric")
        void equalsIsSymmetric() {
            ObjectValue obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj1).isEqualTo(obj2);
            assertThat(obj2).isEqualTo(obj1);
        }

        @Test
        @DisplayName("equals() is transitive")
        void equalsIsTransitive() {
            ObjectValue obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue obj3 = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj1).isEqualTo(obj2);
            assertThat(obj2).isEqualTo(obj3);
            assertThat(obj1).isEqualTo(obj3);
        }

        @Test
        @DisplayName("equals() ignores secret flag")
        void equalsIgnoresSecretFlag() {
            ObjectValue regular = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            assertThat(regular).isEqualTo(secret);
            assertThat(secret).isEqualTo(regular);
        }

        @Test
        @DisplayName("hashCode() is consistent with equals()")
        void hashCodeConsistentWithEquals() {
            ObjectValue obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue obj2 = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj1).isEqualTo(obj2);
            assertThat(obj1.hashCode()).isEqualTo(obj2.hashCode());
        }

        @Test
        @DisplayName("hashCode() ignores secret flag")
        void hashCodeIgnoresSecretFlag() {
            ObjectValue regular = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            assertThat(regular.hashCode()).isEqualTo(secret.hashCode());
        }

        @Test
        @DisplayName("equals() returns false for different content")
        void equalsReturnsFalseForDifferentContent() {
            ObjectValue obj1 = new ObjectValue(Map.of("key", Value.of(1)), false);
            ObjectValue obj2 = new ObjectValue(Map.of("key", Value.of(2)), false);
            
            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for different keys")
        void equalsReturnsFalseForDifferentKeys() {
            ObjectValue obj1 = new ObjectValue(Map.of("key1", Value.of(1)), false);
            ObjectValue obj2 = new ObjectValue(Map.of("key2", Value.of(1)), false);
            
            assertThat(obj1).isNotEqualTo(obj2);
        }

        @Test
        @DisplayName("equals() returns false for null")
        void equalsReturnsFalseForNull() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals() returns false for non-Map object")
        void equalsReturnsFalseForNonMap() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj).isNotEqualTo("not a map");
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("toString() for non-secret shows content")
        void toStringNonSecretShowsContent() {
            ObjectValue obj = new ObjectValue(Map.of("name", Value.of("Alice")), false);
            String result = obj.toString();
            
            assertThat(result).contains("name", "Alice");
            assertThat(result).startsWith("{");
            assertThat(result).endsWith("}");
        }

        @Test
        @DisplayName("toString() for secret shows placeholder")
        void toStringSecretShowsPlaceholder() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), true);
            
            assertThat(obj.toString()).isEqualTo("***SECRET***");
        }

        @Test
        @DisplayName("toString() for empty non-secret shows {}")
        void toStringEmptyShowsBraces() {
            ObjectValue obj = new ObjectValue(Map.of(), false);
            
            assertThat(obj.toString()).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("Builder - Enhanced Coverage")
    class BuilderEnhancedTests {

        @Test
        @DisplayName("Builder with elements and secret propagates secret to accessed elements")
        void builderSecretPropagatesOnAccess() {
            ObjectValue obj = ObjectValue.builder()
                .put("key1", Value.of(1))
                .put("key2", Value.of(2))
                .secret()
                .build();
            
            assertThat(obj.secret()).isTrue();
            assertThat(obj.get("key1").secret()).isTrue();
            assertThat(obj.get("key2").secret()).isTrue();
        }

        @Test
        @DisplayName("Builder can be used multiple times")
        void builderMultipleUse() {
            var builder = ObjectValue.builder();
            
            ObjectValue first = builder.put("k1", Value.of(1)).build();
            ObjectValue second = builder.put("k2", Value.of(2)).build();
            
            assertThat(first).hasSize(1);
            assertThat(second).hasSize(2);
        }

        @Test
        @DisplayName("Builder secret can be set before or after adding properties")
        void builderSecretOrdering() {
            ObjectValue secretFirst = ObjectValue.builder()
                .secret()
                .put("key", Value.of(1))
                .build();
            
            ObjectValue secretLast = ObjectValue.builder()
                .put("key", Value.of(1))
                .secret()
                .build();
            
            assertThat(secretFirst.secret()).isTrue();
            assertThat(secretLast.secret()).isTrue();
            assertThat(secretFirst.get("key").secret()).isTrue();
            assertThat(secretLast.get("key").secret()).isTrue();
        }

        @Test
        @DisplayName("Builder with mixed types creates heterogeneous object")
        void builderMixedTypes() {
            ObjectValue result = ObjectValue.builder()
                .put("number", Value.of(1))
                .put("text", Value.of("hello"))
                .put("bool", Value.of(true))
                .put("null", Value.NULL)
                .build();
            
            assertThat(result).hasSize(4);
            assertThat(result.get("number")).isInstanceOf(NumberValue.class);
            assertThat(result.get("text")).isInstanceOf(TextValue.class);
            assertThat(result.get("bool")).isInstanceOf(BooleanValue.class);
            assertThat(result.get("null")).isInstanceOf(NullValue.class);
        }

        @Test
        @DisplayName("Builder chaining returns same instance")
        void builderChainingReturnsSameInstance() {
            var builder = ObjectValue.builder();
            var afterPut = builder.put("k1", Value.of(1));
            var afterSecret = afterPut.secret();
            var afterPutAll = afterSecret.putAll(Map.of("k2", Value.of(2)));
            
            assertThat(afterPut).isSameAs(builder);
            assertThat(afterSecret).isSameAs(builder);
            assertThat(afterPutAll).isSameAs(builder);
        }

        @Test
        @DisplayName("Builder putAll with empty map works")
        void builderPutAllEmptyMap() {
            ObjectValue result = ObjectValue.builder()
                .putAll(Map.of())
                .build();
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Builder with properties already marked as secret")
        void builderWithSecretProperties() {
            Value secretValue = Value.of(1).asSecret();
            ObjectValue nonSecretObj = ObjectValue.builder()
                .put("key", secretValue)
                .build();
            
            assertThat(nonSecretObj.secret()).isFalse();
            assertThat(nonSecretObj.get("key").secret()).isTrue(); // Values retain their secret flag
            
            ObjectValue secretObj = ObjectValue.builder()
                .put("key", secretValue)
                .secret()
                .build();
            
            assertThat(secretObj.secret()).isTrue();
            assertThat(secretObj.get("key").secret()).isTrue();
        }

        @Test
        @DisplayName("Secret flag semantics: additive, not overriding")
        void secretFlagSemantics() {
            Value nonSecretValue = Value.of(1);
            Value secretValue = Value.of(2).asSecret();
            
            // Non-secret container with non-secret value: value remains non-secret
            ObjectValue nonSecretContainer1 = ObjectValue.builder()
                .put("key", nonSecretValue)
                .build();
            assertThat(nonSecretContainer1.get("key").secret()).isFalse();
            
            // Non-secret container with secret value: value remains secret
            ObjectValue nonSecretContainer2 = ObjectValue.builder()
                .put("key", secretValue)
                .build();
            assertThat(nonSecretContainer2.get("key").secret()).isTrue();
            
            // Secret container with non-secret value: value becomes secret
            ObjectValue secretContainer1 = ObjectValue.builder()
                .put("key", nonSecretValue)
                .secret()
                .build();
            assertThat(secretContainer1.get("key").secret()).isTrue();
            
            // Secret container with secret value: value remains secret
            ObjectValue secretContainer2 = ObjectValue.builder()
                .put("key", secretValue)
                .secret()
                .build();
            assertThat(secretContainer2.get("key").secret()).isTrue();
        }
    }
    @Nested
    @DisplayName("ErrorValue Secret Inheritance - CRITICAL")
    class ErrorValueSecretInheritanceTests {

        @Test
        @DisplayName("ErrorValue from get(null) inherits secret flag")
        void errorFromGetNullInheritsSecret() {
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value error = secret.get(null);
            
            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from get(non-String) inherits secret flag")
        void errorFromGetNonStringInheritsSecret() {
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value error = secret.get(123);
            
            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(null) inherits secret flag")
        void errorFromGetOrDefaultNullInheritsSecret() {
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value error = secret.getOrDefault(null, Value.of(999));
            
            assertThat(error).isInstanceOf(ErrorValue.class);
            assertThat(error.secret()).isTrue();
        }

        @Test
        @DisplayName("ErrorValue from getOrDefault(non-String) inherits secret flag")
        void errorFromGetOrDefaultNonStringInheritsSecret() {
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value error = secret.getOrDefault(456, Value.of(999));
            
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
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value result = secret.getOrDefault("key", Value.of(999));
            
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("getOrDefault() propagates secret for default values")
        void getOrDefaultPropagatesSecretForDefault() {
            ObjectValue secret = new ObjectValue(Map.of("key", Value.of(1)), true);
            Value result = secret.getOrDefault("missing", Value.of(999));
            
            assertThat(result.secret()).isTrue();
        }

        @Test
        @DisplayName("containsKey(null) returns false instead of throwing")
        void containsKeyNullReturnsFalse() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsKey(null)).isFalse();
        }

        @Test
        @DisplayName("containsKey(non-String) returns false instead of throwing")
        void containsKeyNonStringReturnsFalse() {
            ObjectValue obj = new ObjectValue(Map.of("key", Value.of(1)), false);
            
            assertThat(obj.containsKey(123)).isFalse();
        }
    }

    @Nested
    @DisplayName("ToString - Enhanced Coverage")
    class ToStringEnhancedTests {

        @Test
        @DisplayName("toString() with nested objects shows structure")
        void toStringNestedObjects() {
            ObjectValue inner = new ObjectValue(Map.of("a", Value.of(1), "b", Value.of(2)), false);
            ObjectValue outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), false);
            
            String result = outer.toString();
            
            assertThat(result).contains("inner");
            assertThat(result).contains("c");
            assertThat(result).startsWith("{");
            assertThat(result).endsWith("}");
        }

        @Test
        @DisplayName("toString() with secret nested objects hides inner content")
        void toStringSecretNestedObjects() {
            ObjectValue inner = new ObjectValue(Map.of("a", Value.of(1)), true);
            ObjectValue outer = new ObjectValue(Map.of("inner", inner, "c", Value.of(3)), false);
            
            String result = outer.toString();
            
            assertThat(result).contains("***SECRET***");
            assertThat(result).contains("c");
        }

        @Test
        @DisplayName("toString() handles all value types")
        void toStringMixedTypes() {
            ObjectValue obj = new ObjectValue(Map.of(
                "number", Value.of(1),
                "text", Value.of("hello"),
                "bool", Value.of(true),
                "null", Value.NULL,
                "undefined", Value.UNDEFINED,
                "error", Value.error("test")
            ), false);
            
            String result = obj.toString();
            
            assertThat(result).contains("number");
            assertThat(result).contains("text");
            assertThat(result).contains("bool");
            assertThat(result).contains("null");
            assertThat(result).contains("undefined");
            assertThat(result).contains("error");
        }

        @Test
        @DisplayName("toString() with many properties is readable")
        void toStringManyProperties() {
            var builder = ObjectValue.builder();
            for (int i = 0; i < 10; i++) {
                builder.put("key" + i, Value.of(i));
            }
            ObjectValue obj = builder.build();
            
            String result = obj.toString();
            
            assertThat(result).contains("key0");
            assertThat(result).contains("key9");
        }
    }

    @Nested
    @DisplayName("Edge Cases - Additional Coverage")
    class EdgeCasesAdditionalTests {

        @Test
        @DisplayName("Empty keySet iteration")
        void emptyKeySetIteration() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            
            assertThat(empty.keySet()).isEmpty();
            for (String key : empty.keySet()) {
                fail("Should not iterate over empty keySet");
            }
        }

        @Test
        @DisplayName("Empty values iteration")
        void emptyValuesIteration() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            
            assertThat(empty.values()).isEmpty();
            for (Value value : empty.values()) {
                fail("Should not iterate over empty values");
            }
        }

        @Test
        @DisplayName("Empty entrySet iteration")
        void emptyEntrySetIteration() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            
            assertThat(empty.entrySet()).isEmpty();
            for (var entry : empty.entrySet()) {
                fail("Should not iterate over empty entrySet");
            }
        }

        @Test
        @DisplayName("forEach on empty object")
        void forEachOnEmpty() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            List<String> visited = new ArrayList<>();
            
            empty.forEach((k, v) -> visited.add(k));
            
            assertThat(visited).isEmpty();
        }

        @Test
        @DisplayName("get() on empty object returns null")
        void getOnEmptyReturnsNull() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            
            assertThat(empty.get("anything")).isNull();
        }

        @Test
        @DisplayName("getOrDefault() on empty object returns default")
        void getOrDefaultOnEmptyReturnsDefault() {
            ObjectValue empty = new ObjectValue(Map.of(), false);
            Value defaultValue = Value.of(999);
            
            Value result = empty.getOrDefault("anything", defaultValue);
            
            assertThat(result).isEqualTo(defaultValue);
        }
    }

    @Nested
    @DisplayName("Pattern Matching Examples")
    class PatternMatchingTests {

        @Test
        @DisplayName("Pattern matching for resource access control")
        void patternMatchingResourceAccess() {
            Value resourceData = Value.ofObject(Map.of(
                "resourceId", Value.of("doc-123"),
                "owner", Value.of("alice")
            ));

            String decision = switch (resourceData) {
                case ObjectValue obj when obj.get("owner") instanceof TextValue(String owner, boolean i)
                    && "alice".equals(owner) -> "Access granted to owner";
                case ObjectValue obj when obj.containsKey("resourceId") -> "Read-only access";
                case ObjectValue o -> "No access";
                default -> "Invalid resource data";
            };

            assertThat(decision).isEqualTo("Access granted to owner");
        }

        @Test
        @DisplayName("Pattern matching with null handling")
        void patternMatchingNullHandling() {
            Value userData = Value.ofObject(Map.of(
                "username", Value.of("bob"),
                "email", Value.NULL
            ));

            boolean hasEmail = switch (userData) {
                case ObjectValue obj -> {
                    Value email = obj.get("email");
                    yield !(email instanceof NullValue);
                }
                default -> false;
            };

            assertThat(hasEmail).isFalse();
        }
    }

}