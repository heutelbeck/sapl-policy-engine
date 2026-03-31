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

import java.util.Arrays;

import io.sapl.ast.BinaryOperatorType;
import lombok.experimental.UtilityClass;

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
