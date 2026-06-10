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
package io.sapl.compiler.expressions;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.ObjectCompiler.PureObject;
import io.sapl.compiler.expressions.ObjectCompiler.StreamObject;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ObjectCompiler.PureObject and StreamObject record contracts")
class ObjectCompilerRecordsTests {

    private static final SourceLocation LOCATION = new SourceLocation("doc", "{}", 0, 2, 1, 1);

    private static List<CompiledExpression> entries() {
        return List.of(Value.of("alpha"), Value.of(42));
    }

    @Nested
    @DisplayName("PureObject")
    class PureObjectContracts {

        @Test
        @DisplayName("two PureObjects with equal key contents and entries are equal and share a hashCode")
        void whenSameContentsThenEqual() {
            val a = new PureObject(new String[] { "k1", "k2" }, entries(), LOCATION);
            val b = new PureObject(new String[] { "k1", "k2" }, entries(), LOCATION);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("differing key contents make PureObjects unequal")
        void whenKeysDifferThenNotEqual() {
            val a = new PureObject(new String[] { "k1", "k2" }, entries(), LOCATION);
            val b = new PureObject(new String[] { "k1", "kX" }, entries(), LOCATION);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString renders the keys array contents (not the array's identity)")
        void toStringIncludesKeyContents() {
            val po = new PureObject(new String[] { "alpha", "beta" }, entries(), LOCATION);

            assertThat(po.toString()).contains("alpha").contains("beta").doesNotContain("[Ljava.lang.String");
        }
    }

    @Nested
    @DisplayName("StreamObject")
    class StreamObjectContracts {

        @Test
        @DisplayName("two StreamObjects with equal key contents and entries are equal and share a hashCode")
        void whenSameContentsThenEqual() {
            val a = new StreamObject(new String[] { "k1", "k2" }, entries());
            val b = new StreamObject(new String[] { "k1", "k2" }, entries());

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("differing key contents make StreamObjects unequal")
        void whenKeysDifferThenNotEqual() {
            val a = new StreamObject(new String[] { "k1", "k2" }, entries());
            val b = new StreamObject(new String[] { "kX", "k2" }, entries());

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString renders the keys array contents (not the array's identity)")
        void toStringIncludesKeyContents() {
            val so = new StreamObject(new String[] { "alpha", "beta" }, entries());

            assertThat(so.toString()).contains("alpha").contains("beta").doesNotContain("[Ljava.lang.String");
        }
    }
}
