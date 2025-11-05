package io.sapl.api.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Value Factory Tests")
class ValueTests {

    @Nested
    @DisplayName("Boolean Factory")
    class BooleanFactoryTests {

        @Test
        @DisplayName("of(true) returns TRUE singleton")
        void ofTrueReturnsSingleton() {
            Value result = Value.of(true);
            
            assertThat(result).isSameAs(Value.TRUE);
            assertThat(result).isInstanceOf(BooleanValue.class);
        }

        @Test
        @DisplayName("of(false) returns FALSE singleton")
        void ofFalseReturnsSingleton() {
            Value result = Value.of(false);
            
            assertThat(result).isSameAs(Value.FALSE);
            assertThat(result).isInstanceOf(BooleanValue.class);
        }

        @Test
        @DisplayName("Multiple calls return same singleton")
        void multipleCalls() {
            assertThat(Value.of(true)).isSameAs(Value.of(true));
            assertThat(Value.of(false)).isSameAs(Value.of(false));
        }
    }

    @Nested
    @DisplayName("Long Factory")
    class LongFactoryTests {

        @Test
        @DisplayName("of(0L) returns ZERO singleton")
        void ofZeroReturnsSingleton() {
            Value result = Value.of(0L);
            
            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(1L) returns ONE singleton")
        void ofOneReturnsSingleton() {
            Value result = Value.of(1L);
            
            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(10L) returns TEN singleton")
        void ofTenReturnsSingleton() {
            Value result = Value.of(10L);
            
            assertThat(result).isSameAs(Value.TEN);
        }

        @ParameterizedTest
        @ValueSource(longs = {2L, 5L, 42L, 100L, 1000L, -1L, -10L, -100L})
        @DisplayName("of(other values) creates new NumberValue")
        void ofOtherValues(long value) {
            Value result = Value.of(value);
            
            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(value));
        }

        @Test
        @DisplayName("of(Long.MAX_VALUE) does not overflow")
        void ofMaxValue() {
            Value result = Value.of(Long.MAX_VALUE);
            
            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE));
        }

        @Test
        @DisplayName("of(Long.MIN_VALUE) does not overflow")
        void ofMinValue() {
            Value result = Value.of(Long.MIN_VALUE);
            
            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(Long.MIN_VALUE));
        }

        @Test
        @DisplayName("of(4294967296L) does not truncate to 0")
        void ofLargeValueDoesNotTruncate() {
            long value = 4_294_967_296L; // 2^32, would truncate to 0 with (int) cast
            Value result = Value.of(value);
            
            assertThat(result).isNotSameAs(Value.ZERO);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(new BigDecimal("4294967296"));
        }

        @Test
        @DisplayName("of(4294967297L) does not truncate to 1")
        void ofLargeValuePlusOneDoesNotTruncate() {
            long value = 4_294_967_297L; // 2^32 + 1, would truncate to 1 with (int) cast
            Value result = Value.of(value);
            
            assertThat(result).isNotSameAs(Value.ONE);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(new BigDecimal("4294967297"));
        }
    }

    @Nested
    @DisplayName("Double Factory")
    class DoubleFactoryTests {

        @ParameterizedTest
        @ValueSource(doubles = {0.0, -0.0})
        @DisplayName("of(0.0 variants) returns ZERO singleton")
        void ofZeroReturnsSingleton(double value) {
            Value result = Value.of(value);
            
            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(1.0) returns ONE singleton")
        void ofOneReturnsSingleton() {
            Value result = Value.of(1.0);
            
            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(10.0) returns TEN singleton")
        void ofTenReturnsSingleton() {
            Value result = Value.of(10.0);
            
            assertThat(result).isSameAs(Value.TEN);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.5, 3.14159, 2.71828, -1.0, 0.001, 1e10, 1e-10})
        @DisplayName("of(other values) creates new NumberValue")
        void ofOtherValues(double value) {
            Value result = Value.of(value);
            
            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(BigDecimal.valueOf(value));
        }

        @Test
        @DisplayName("of(NaN) throws IllegalArgumentException")
        void ofNaNThrows() {
            assertThatThrownBy(() -> Value.of(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create Value from NaN")
                .hasMessageContaining("Use Value.error()");
        }

        @Test
        @DisplayName("of(POSITIVE_INFINITY) throws IllegalArgumentException")
        void ofPositiveInfinityThrows() {
            assertThatThrownBy(() -> Value.of(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create Value from infinite double")
                .hasMessageContaining("Use Value.error()");
        }

        @Test
        @DisplayName("of(NEGATIVE_INFINITY) throws IllegalArgumentException")
        void ofNegativeInfinityThrows() {
            assertThatThrownBy(() -> Value.of(Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot create Value from infinite double")
                .hasMessageContaining("Use Value.error()");
        }
    }

    @Nested
    @DisplayName("BigDecimal Factory")
    class BigDecimalFactoryTests {

        @Test
        @DisplayName("of(BigDecimal.ZERO) returns ZERO singleton")
        void ofZeroReturnsSingleton() {
            Value result = Value.of(BigDecimal.ZERO);
            
            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(BigDecimal.ONE) returns ONE singleton")
        void ofOneReturnsSingleton() {
            Value result = Value.of(BigDecimal.ONE);
            
            assertThat(result).isSameAs(Value.ONE);
        }

        @Test
        @DisplayName("of(BigDecimal.TEN) returns TEN singleton")
        void ofTenReturnsSingleton() {
            Value result = Value.of(BigDecimal.TEN);
            
            assertThat(result).isSameAs(Value.TEN);
        }

        @Test
        @DisplayName("of(new BigDecimal(0)) returns ZERO singleton")
        void ofNewZeroReturnsSingleton() {
            Value result = Value.of(new BigDecimal("0"));
            
            assertThat(result).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("of(new BigDecimal(0.00)) returns ZERO singleton")
        void ofZeroWithScaleReturnsSingleton() {
            Value result = Value.of(new BigDecimal("0.00"));
            
            assertThat(result).isSameAs(Value.ZERO);
        }

        @ParameterizedTest
        @MethodSource("provideBigDecimalValues")
        @DisplayName("of(other values) creates new NumberValue")
        void ofOtherValues(BigDecimal value) {
            Value result = Value.of(value);
            
            assertThat(result).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) result).value()).isEqualByComparingTo(value);
        }

        static Stream<BigDecimal> provideBigDecimalValues() {
            return Stream.of(
                new BigDecimal("3.14159"),
                new BigDecimal("2.71828"),
                new BigDecimal("-1.5"),
                new BigDecimal("1000000000000"),
                new BigDecimal("0.000000001")
            );
        }

        @Test
        @DisplayName("of(null) throws NullPointerException")
        void ofNullThrows() {
            assertThatThrownBy(() -> Value.of((BigDecimal) null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("String Factory")
    class StringFactoryTests {

        @Test
        @DisplayName("of(empty string) returns EMPTY_TEXT singleton")
        void ofEmptyStringReturnsSingleton() {
            Value result = Value.of("");
            
            assertThat(result).isSameAs(Value.EMPTY_TEXT);
        }

        @ParameterizedTest
        @ValueSource(strings = {"hello", "world", "test", " ", "  ", "\t", "\n"})
        @DisplayName("of(non-empty strings) creates new TextValue")
        void ofNonEmptyStrings(String value) {
            Value result = Value.of(value);
            
            assertThat(result).isInstanceOf(TextValue.class);
            assertThat(((TextValue) result).value()).isEqualTo(value);
        }

        @Test
        @DisplayName("of(null) throws NullPointerException")
        void ofNullThrows() {
            assertThatThrownBy(() -> Value.of((String) null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Array Factory")
    class ArrayFactoryTests {

        @Test
        @DisplayName("ofArray() with no args returns EMPTY_ARRAY singleton")
        void ofArrayNoArgsReturnsSingleton() {
            Value result = Value.ofArray();
            
            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("ofArray(empty list) returns EMPTY_ARRAY singleton")
        void ofArrayEmptyListReturnsSingleton() {
            Value result = Value.ofArray(List.of());
            
            assertThat(result).isSameAs(Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("ofArray(varargs) creates ArrayValue")
        void ofArrayVarargs() {
            Value result = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
            
            assertThat(result).isInstanceOf(ArrayValue.class);
            ArrayValue array = (ArrayValue) result;
            assertThat(array).hasSize(3);
            assertThat(array.get(0)).isEqualTo(Value.of(1));
            assertThat(array.get(1)).isEqualTo(Value.of(2));
            assertThat(array.get(2)).isEqualTo(Value.of(3));
        }

        @Test
        @DisplayName("ofArray(list) creates ArrayValue")
        void ofArrayList() {
            List<Value> list = List.of(Value.of("a"), Value.of("b"));
            Value result = Value.ofArray(list);
            
            assertThat(result).isInstanceOf(ArrayValue.class);
            ArrayValue array = (ArrayValue) result;
            assertThat(array).hasSize(2);
            assertThat(array.get(0)).isEqualTo(Value.of("a"));
            assertThat(array.get(1)).isEqualTo(Value.of("b"));
        }

        @Test
        @DisplayName("ofArray(null list) throws NullPointerException")
        void ofArrayNullListThrows() {
            assertThatThrownBy(() -> Value.ofArray((List<Value>) null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Object Factory")
    class ObjectFactoryTests {

        @Test
        @DisplayName("ofObject(empty map) returns EMPTY_OBJECT singleton")
        void ofObjectEmptyMapReturnsSingleton() {
            Value result = Value.ofObject(Map.of());
            
            assertThat(result).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("ofObject(map) creates ObjectValue")
        void ofObjectMap() {
            Map<String, Value> map = Map.of(
                "name", Value.of("Alice"),
                "age", Value.of(30)
            );
            Value result = Value.ofObject(map);
            
            assertThat(result).isInstanceOf(ObjectValue.class);
            ObjectValue obj = (ObjectValue) result;
            assertThat(obj).hasSize(2);
            assertThat(obj.get("name")).isEqualTo(Value.of("Alice"));
            assertThat(obj.get("age")).isEqualTo(Value.of(30));
        }

        @Test
        @DisplayName("ofObject(null) throws NullPointerException")
        void ofObjectNullThrows() {
            assertThatThrownBy(() -> Value.ofObject(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Error Factory")
    class ErrorFactoryTests {

        @Test
        @DisplayName("error(message) creates ErrorValue")
        void errorWithMessage() {
            Value result = Value.error("Test error");
            
            assertThat(result).isInstanceOf(ErrorValue.class);
            ErrorValue error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Test error");
            assertThat(error.cause()).isNull();
            assertThat(error.secret()).isFalse();
        }

        @Test
        @DisplayName("error(message, cause) creates ErrorValue")
        void errorWithMessageAndCause() {
            Exception cause = new RuntimeException("Cause");
            Value result = Value.error("Test error", cause);
            
            assertThat(result).isInstanceOf(ErrorValue.class);
            ErrorValue error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Test error");
            assertThat(error.cause()).isSameAs(cause);
            assertThat(error.secret()).isFalse();
        }

        @Test
        @DisplayName("error(cause) creates ErrorValue with cause message")
        void errorWithCause() {
            Exception cause = new RuntimeException("Cause message");
            Value result = Value.error(cause);
            
            assertThat(result).isInstanceOf(ErrorValue.class);
            ErrorValue error = (ErrorValue) result;
            assertThat(error.message()).isEqualTo("Cause message");
            assertThat(error.cause()).isSameAs(cause);
            assertThat(error.secret()).isFalse();
        }

        @Test
        @DisplayName("error(null message) throws NullPointerException")
        void errorNullMessageThrows() {
            assertThatThrownBy(() -> Value.error((String) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("error(null cause) throws NullPointerException")
        void errorNullCauseThrows() {
            assertThatThrownBy(() -> Value.error((Throwable) null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("TRUE is BooleanValue(true)")
        void trueConstant() {
            assertThat(Value.TRUE).isInstanceOf(BooleanValue.class);
            assertThat(((BooleanValue) Value.TRUE).value()).isTrue();
            assertThat(Value.TRUE.secret()).isFalse();
        }

        @Test
        @DisplayName("FALSE is BooleanValue(false)")
        void falseConstant() {
            assertThat(Value.FALSE).isInstanceOf(BooleanValue.class);
            assertThat(((BooleanValue) Value.FALSE).value()).isFalse();
            assertThat(Value.FALSE.secret()).isFalse();
        }

        @Test
        @DisplayName("NULL is NullValue")
        void nullConstant() {
            assertThat(Value.NULL).isInstanceOf(NullValue.class);
            assertThat(Value.NULL.secret()).isFalse();
        }

        @Test
        @DisplayName("UNDEFINED is UndefinedValue")
        void undefinedConstant() {
            assertThat(Value.UNDEFINED).isInstanceOf(UndefinedValue.class);
            assertThat(Value.UNDEFINED.secret()).isFalse();
        }

        @Test
        @DisplayName("ZERO is NumberValue(0)")
        void zeroConstant() {
            assertThat(Value.ZERO).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(Value.ZERO.secret()).isFalse();
        }

        @Test
        @DisplayName("ONE is NumberValue(1)")
        void oneConstant() {
            assertThat(Value.ONE).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.ONE).value()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(Value.ONE.secret()).isFalse();
        }

        @Test
        @DisplayName("TEN is NumberValue(10)")
        void tenConstant() {
            assertThat(Value.TEN).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) Value.TEN).value()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(Value.TEN.secret()).isFalse();
        }

        @Test
        @DisplayName("EMPTY_ARRAY is empty ArrayValue")
        void emptyArrayConstant() {
            assertThat(Value.EMPTY_ARRAY).isInstanceOf(ArrayValue.class);
            assertThat(Value.EMPTY_ARRAY.size()).isEqualTo(0);
            assertThat(Value.EMPTY_ARRAY.secret()).isFalse();
        }

        @Test
        @DisplayName("EMPTY_OBJECT is empty ObjectValue")
        void emptyObjectConstant() {
            assertThat(Value.EMPTY_OBJECT).isInstanceOf(ObjectValue.class);
            assertThat(Value.EMPTY_OBJECT.size()).isEqualTo(0);
            assertThat(Value.EMPTY_OBJECT.secret()).isFalse();
        }

        @Test
        @DisplayName("EMPTY_TEXT is empty TextValue")
        void emptyTextConstant() {
            assertThat(Value.EMPTY_TEXT).isInstanceOf(TextValue.class);
            assertThat(((TextValue) Value.EMPTY_TEXT).value()).isEmpty();
            assertThat(Value.EMPTY_TEXT.secret()).isFalse();
        }

        @Test
        @DisplayName("All constants are not secret")
        void constantsAreNotSecret() {
            assertThat(Value.TRUE.secret()).isFalse();
            assertThat(Value.FALSE.secret()).isFalse();
            assertThat(Value.NULL.secret()).isFalse();
            assertThat(Value.UNDEFINED.secret()).isFalse();
            assertThat(Value.ZERO.secret()).isFalse();
            assertThat(Value.ONE.secret()).isFalse();
            assertThat(Value.TEN.secret()).isFalse();
            assertThat(Value.EMPTY_ARRAY.secret()).isFalse();
            assertThat(Value.EMPTY_OBJECT.secret()).isFalse();
            assertThat(Value.EMPTY_TEXT.secret()).isFalse();
        }
    }

    @Nested
    @DisplayName("Pattern Matching Examples")
    class PatternMatchingTests {

        @Test
        @DisplayName("Pattern matching for policy decision")
        void patternMatchingPolicyDecision() {
            Value policyResult = Value.of(true);

            String decision = switch (policyResult) {
                case BooleanValue(boolean allowed, boolean ignore) -> allowed ? "PERMIT" : "DENY";
                case ErrorValue ignore -> "INDETERMINATE";
                case UndefinedValue ignore -> "NOT_APPLICABLE";
                default -> "INDETERMINATE";
            };

            assertThat(decision).isEqualTo("PERMIT");
        }

        @Test
        @DisplayName("Pattern matching with guards for access control")
        void patternMatchingWithGuards() {
            Value clearanceLevel = Value.of(7);

            String access = switch (clearanceLevel) {
                case NumberValue(BigDecimal level, boolean ignore) when level.compareTo(BigDecimal.valueOf(10)) >= 0 ->
                    "TOP SECRET";
                case NumberValue(BigDecimal level, boolean ignore) when level.compareTo(BigDecimal.valueOf(5)) >= 0 ->
                    "SECRET";
                case NumberValue(BigDecimal level, boolean ignore) when level.compareTo(BigDecimal.ZERO) > 0 ->
                    "CONFIDENTIAL";
                default -> "PUBLIC";
            };

            assertThat(access).isEqualTo("SECRET");
        }

        @Test
        @DisplayName("Pattern matching for attribute extraction")
        void patternMatchingAttributeExtraction() {
            Value user = Value.ofObject(Map.of(
                "name", Value.of("alice"),
                "role", Value.of("admin")
            ));

            boolean isAdmin = switch (user) {
                case ObjectValue obj -> {
                    Value role = obj.get("role");
                    yield role instanceof TextValue(String r, boolean ignore) && "admin".equals(r);
                }
                default -> false;
            };

            assertThat(isAdmin).isTrue();
        }
    }

    @Nested
    @DisplayName("Factory Edge Cases")
    class FactoryEdgeCasesTests {

        @Test
        @DisplayName("ofArray with varargs consistency")
        void ofArrayVarargsConsistency() {
            Value v1 = Value.ofArray(Value.of(1), Value.of(2));
            Value v2 = Value.ofArray(Value.of(1), Value.of(2));
            
            assertThat(v1).isEqualTo(v2);
        }

        @Test
        @DisplayName("ofObject creates defensive copy")
        void ofObjectDefensiveCopy() {
            Map<String, Value> mutableMap = new HashMap<>();
            mutableMap.put("key", Value.of(1));
            
            Value obj = Value.ofObject(mutableMap);
            mutableMap.put("key2", Value.of(2));
            
            assertThat(((ObjectValue) obj).size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Factory methods return appropriate types")
        void factoryMethodTypes() {
            assertThat(Value.of(true)).isInstanceOf(BooleanValue.class);
            assertThat(Value.of(1L)).isInstanceOf(NumberValue.class);
            assertThat(Value.of(1.5)).isInstanceOf(NumberValue.class);
            assertThat(Value.of(BigDecimal.ONE)).isInstanceOf(NumberValue.class);
            assertThat(Value.of("text")).isInstanceOf(TextValue.class);
            assertThat(Value.ofArray()).isInstanceOf(ArrayValue.class);
            assertThat(Value.ofObject(Map.of())).isInstanceOf(ObjectValue.class);
            assertThat(Value.error("error")).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("Singleton constants are consistent")
        void singletonConsistency() {
            assertThat(Value.of(true)).isSameAs(Value.TRUE);
            assertThat(Value.of(false)).isSameAs(Value.FALSE);
            assertThat(Value.of(0L)).isSameAs(Value.ZERO);
            assertThat(Value.of(1L)).isSameAs(Value.ONE);
            assertThat(Value.of(10L)).isSameAs(Value.TEN);
            assertThat(Value.of("")).isSameAs(Value.EMPTY_TEXT);
            assertThat(Value.ofArray()).isSameAs(Value.EMPTY_ARRAY);
            assertThat(Value.ofObject(Map.of())).isSameAs(Value.EMPTY_OBJECT);
        }

        @Test
        @DisplayName("Double zero variants return same singleton")
        void doubleZeroVariants() {
            assertThat(Value.of(0.0)).isSameAs(Value.ZERO);
            assertThat(Value.of(-0.0)).isSameAs(Value.ZERO);
        }

        @Test
        @DisplayName("BigDecimal with different scales but same value are equal")
        void bigDecimalScaleEquality() {
            Value v1 = Value.of(new BigDecimal("1.0"));
            Value v2 = Value.of(new BigDecimal("1.00"));
            
            assertThat(v1).isEqualTo(v2);
        }

        @Test
        @DisplayName("Long values beyond int range")
        void longValuesBeyondIntRange() {
            long large = 10_000_000_000L; // Beyond int range
            Value value = Value.of(large);
            
            assertThat(value).isInstanceOf(NumberValue.class);
            NumberValue numValue = (NumberValue) value;
            assertThat(numValue.value()).isEqualByComparingTo(BigDecimal.valueOf(large));
        }

        @Test
        @DisplayName("Array with mixed value types")
        void arrayWithMixedTypes() {
            Value array = Value.ofArray(
                Value.of(1),
                Value.of("text"),
                Value.of(true),
                Value.NULL,
                Value.UNDEFINED
            );
            
            assertThat(array).isInstanceOf(ArrayValue.class);
            assertThat(((ArrayValue) array).size()).isEqualTo(5);
        }

        @Test
        @DisplayName("Object with all value types")
        void objectWithAllTypes() {
            Value obj = Value.ofObject(Map.of(
                "number", Value.of(1),
                "text", Value.of("hello"),
                "bool", Value.of(true),
                "null", Value.NULL
            ));
            
            assertThat(obj).isInstanceOf(ObjectValue.class);
            assertThat(((ObjectValue) obj).size()).isEqualTo(4);
        }

        @Test
        @DisplayName("Error factory with null cause is allowed")
        void errorFactoryNullCause() {
            Value error = Value.error("message", null);
            
            assertThat(error).isInstanceOf(ErrorValue.class);
            ErrorValue errorValue = (ErrorValue) error;
            assertThat(errorValue.message()).isEqualTo("message");
            assertThat(errorValue.cause()).isNull();
        }
    }

}