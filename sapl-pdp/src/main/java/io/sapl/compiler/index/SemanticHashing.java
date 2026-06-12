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
package io.sapl.compiler.index;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperatorType;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

/**
 * Utility for computing semantic hashes of compiled expressions. These hashes
 * identify semantically equivalent expressions across policies, enabling the
 * canonical policy index to evaluate each unique predicate at most once.
 * <p>
 * The hashes are only meaningful within a single JVM session and compilation.
 * They are not suitable for persistence or cross-process comparison.
 * <p>
 * <b>Algorithm choice:</b> Uses MurmurHash3's 64-bit finalizer ({@code fmix64})
 * for bit mixing and a Boost-style {@code hash_combine} for accumulation.
 * <ul>
 * <li>{@code fmix64} provides full avalanche: every input bit affects every
 * output bit. This is critical for avoiding systematic collisions when hashing
 * structurally similar expressions (e.g., same operator, different operand
 * order).</li>
 * <li>{@code hash_combine} is asymmetric:
 * {@code combine(a, b) != combine(b, a)}.
 * This ensures that operand order is preserved for non-commutative operators.
 * The combination uses XOR with shifted copies of the accumulator plus a golden
 * ratio constant, following the Boost C++ hash_combine pattern adapted to
 * 64-bit.</li>
 * </ul>
 * For commutative operators, child hashes are sorted before combining, so
 * operand order does not affect the result.
 */
@UtilityClass
public class SemanticHashing {

    /**
     * Generates a stable kind constant from a class. Used to distinguish
     * different operator types in the hash.
     *
     * @param clazz the operator record class
     * @return a hash constant unique per class name
     */
    public static long kindHash(Class<?> clazz) {
        return fmix64(clazz.getName().hashCode());
    }

    private static final long TEXT_KIND         = kindHash(TextValue.class);
    private static final long NUMBER_KIND       = kindHash(NumberValue.class);
    private static final long BOOLEAN_KIND      = kindHash(BooleanValue.class);
    private static final long NULL_KIND         = kindHash(NullValue.class);
    private static final long UNDEFINED_KIND    = kindHash(UndefinedValue.class);
    private static final long ERROR_KIND        = kindHash(ErrorValue.class);
    private static final long ARRAY_KIND        = kindHash(ArrayValue.class);
    private static final long OBJECT_KIND       = kindHash(ObjectValue.class);
    private static final long OBJECT_ENTRY_KIND = fmix64(OBJECT_KIND);
    private static final long ZERO_NUMBER_HASH  = ordered(NUMBER_KIND, 0L);

    /**
     * Computes a full 64-bit content hash of a constant value, consistent with
     * {@link Value} equality. Equal values produce equal hashes. Unlike
     * the 32-bit {@link Object#hashCode()} of the value records, this folds the
     * entire content through {@link #fmix64} so that distinct constants do not
     * share identity in the policy index through dense 32-bit collisions (for
     * example {@code "Aa"} and {@code "BB"}, which collide under
     * {@link String#hashCode()}).
     *
     * @param value the constant value to hash
     * @return a 64-bit content hash consistent with value equality
     */
    public static long valueHash(Value value) {
        return switch (value) {
        case TextValue(var text)     -> ordered(TEXT_KIND, textHash(text));
        case NumberValue(var number) -> numberHash(number);
        case BooleanValue(var bool)  -> ordered(BOOLEAN_KIND, bool ? 1L : 0L);
        case NullValue ignored       -> NULL_KIND;
        case UndefinedValue ignored  -> UNDEFINED_KIND;
        case ErrorValue error        -> ordered(ERROR_KIND, textHash(error.message()),
                error.cause() == null ? 0L : textHash(error.cause().getClass().getName()));
        case ArrayValue array        -> arrayHash(array);
        case ObjectValue object      -> objectHash(object);
        };
    }

    /**
     * Computes a full 64-bit content hash of a string, avoiding the dense
     * collisions of {@link String#hashCode()}. Every character is folded
     * through {@link #fmix64}, so short equal-length strings do not collide.
     *
     * @param text the string to hash, may be null
     * @return a 64-bit content hash, a fixed constant for null
     */
    public static long textHash(String text) {
        if (text == null) {
            return NULL_KIND;
        }
        long hash = fmix64(text.length());
        for (var i = 0; i < text.length(); i++) {
            hash = combine(hash, text.charAt(i));
        }
        return hash;
    }

    private static long numberHash(BigDecimal number) {
        if (number.signum() == 0) {
            return ZERO_NUMBER_HASH;
        }
        BigDecimal normalized;
        try {
            // Numerical equality treats 1.0 and 1.00 as equal, so hash the
            // canonical form. Extreme scales can overflow stripping; the raw
            // value then keeps the hash defined and equal for equal inputs.
            normalized = number.stripTrailingZeros();
        } catch (ArithmeticException ignored) {
            normalized = number;
        }
        long hash = ordered(NUMBER_KIND, normalized.scale());
        for (val b : normalized.unscaledValue().toByteArray()) {
            hash = combine(hash, b);
        }
        return hash;
    }

    private static long arrayHash(Iterable<Value> array) {
        long hash = fmix64(ARRAY_KIND);
        for (val element : array) {
            hash = combine(hash, valueHash(element));
        }
        return hash;
    }

    private static long objectHash(Map<String, Value> object) {
        // Object equality is order-independent, so accumulate entry hashes
        // through a commutative sum before mixing.
        long entries = 0L;
        for (val entry : object.entrySet()) {
            entries += ordered(OBJECT_ENTRY_KIND, textHash(entry.getKey()), valueHash(entry.getValue()));
        }
        return ordered(OBJECT_KIND, entries);
    }

    /**
     * Combines a kind constant with child hashes in order. Use for
     * non-commutative operators where operand order matters.
     *
     * @param kind unique constant identifying the operator type
     * @param childHashes hashes of child expressions, in order
     * @return combined hash
     */
    public static long ordered(long kind, long... childHashes) {
        long hash = fmix64(kind);
        for (long child : childHashes) {
            hash = combine(hash, child);
        }
        return hash;
    }

    /**
     * Combines a kind constant with child hashes independent of order. Use for
     * commutative operators where operand order does not affect semantics.
     * <p>
     * Sorts the child hashes before combining, so
     * {@code commutative(k, a, b) == commutative(k, b, a)}.
     *
     * @param kind unique constant identifying the operator type
     * @param childHashes hashes of child expressions, in any order
     * @return combined hash
     */
    public static long commutative(long kind, long... childHashes) {
        Arrays.sort(childHashes);
        return ordered(kind, childHashes);
    }

    /**
     * Combines a binary operator type with two operand hashes, respecting the
     * operator's commutativity. Delegates to {@link #commutative} or
     * {@link #ordered} based on {@link BinaryOperatorType#isCommutative()}.
     *
     * @param opType the binary operator type
     * @param leftHash hash of the left operand
     * @param rightHash hash of the right operand
     * @return combined hash
     */
    public static long binaryOp(BinaryOperatorType opType, long leftHash, long rightHash) {
        if (opType.isCommutative()) {
            return commutative(opType.hashCode(), leftHash, rightHash);
        }
        return ordered(opType.hashCode(), leftHash, rightHash);
    }

    /**
     * MurmurHash3 64-bit finalizer. Provides full avalanche: every input bit
     * affects every output bit with probability ~0.5. This is the standard
     * finalizer from Austin Appleby's MurmurHash3, also used in
     * {@code java.util.SplittableRandom} and widely analyzed for quality.
     *
     * @param h the value to mix
     * @return mixed value with full avalanche properties
     */
    private static long fmix64(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /**
     * Boost-style hash_combine adapted to 64-bit. Asymmetric: the order of
     * accumulation matters, i.e., {@code combine(combine(seed, a), b)} differs
     * from {@code combine(combine(seed, b), a)}. This property is essential for
     * non-commutative operators where operand order is semantically significant.
     * <p>
     * The golden ratio constant {@code 0x9E3779B97F4A7C15} (2^64 / phi)
     * ensures good distribution even for sequential or low-entropy inputs. The
     * left/right shifts mix high and low bits of the accumulator into the
     * combination.
     *
     * @param seed the accumulator from previous combinations
     * @param value the next hash to incorporate
     * @return combined hash
     */
    private static long combine(long seed, long value) {
        return seed ^ (fmix64(value) + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
    }

}
