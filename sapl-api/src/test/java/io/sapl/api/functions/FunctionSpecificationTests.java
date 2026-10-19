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
package io.sapl.api.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("Function specification names")
class FunctionSpecificationTests {

    @MethodSource("validNames")
    @ParameterizedTest(name = "{0}.{1}")
    @DisplayName("valid names are accepted and retain their qualified name")
    void whenNameValidThenQualifiedNameIsRetained(String namespace, String functionName, String qualifiedName) {
        val specification = specification(namespace, functionName);

        assertThat(specification.functionName()).isEqualTo(qualifiedName);
    }

    @MethodSource("invalidNames")
    @ParameterizedTest(name = "namespace='{0}', function='{1}'")
    @DisplayName("invalid names are rejected during construction")
    void whenNameInvalidThenConstructionRejectsIt(String namespace, String functionName) {
        assertThatThrownBy(() -> specification(namespace, functionName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(namespace + "." + functionName);
    }

    static Stream<Arguments> validNames() {
        return Stream.of(arguments("time", "dayOfWeek", "time.dayOfWeek"),
                arguments("geo.traccar", "distance2", "geo.traccar.distance2"),
                arguments("digest", "sha3_256", "digest.sha3_256"),
                arguments("graph", "reachable_paths", "graph.reachable_paths"),
                arguments("_library", "$function2", "_library.$function2"),
                arguments("a.b.c.d.e.f.g.h.i", "j", "a.b.c.d.e.f.g.h.i.j"));
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(arguments("bad namespace!", "bad function!"), arguments("", "read"), arguments("library", ""),
                arguments("", ""), arguments("1library", "read"), arguments("library", "1function"),
                arguments("library..nested", "read"), arguments("library", "read!"),
                arguments("a.b.c.d.e.f.g.h.i", "j.k"));
    }

    private static FunctionSpecification specification(String namespace, String functionName) {
        return new FunctionSpecification(namespace, functionName, List.of(), null, ignored -> Value.TRUE);
    }
}
