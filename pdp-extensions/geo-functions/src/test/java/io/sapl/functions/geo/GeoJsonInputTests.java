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
package io.sapl.functions.geo;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("GeoJSON coordinate reference systems")
class GeoJsonInputTests {

    @MethodSource("asciiCrsCodes")
    @ParameterizedTest(name = "{0}")
    @DisplayName("ASCII codes retain their geometry")
    void whenCrsCodeUsesAsciiDigitsThenTheGeometryIsAvailable(String name, String code) {
        val geometry = pointWithCrs(code);
        assertThat(isSimple(geometry)).as(name).isEqualTo(Value.TRUE);
    }

    static Stream<Arguments> asciiCrsCodes() {
        return Stream.of(arguments("plain", "4326"), arguments("positive sign", "+4326"),
                arguments("negative sign", "-4326"));
    }

    @MethodSource("nonAsciiCrsCodes")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Non-ASCII numeric codes are refused")
    void whenCrsCodeUsesNonAsciiDigitsThenTheGeometryIsRefused(String name, String code) {
        val geometry = pointWithCrs(code);
        assertThat(isSimple(geometry)).as(name).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> nonAsciiCrsCodes() {
        return Stream.of(arguments("Arabic digits", "\u0664\u0663\u0662\u0666"),
                arguments("fullwidth digits", "\uFF14\uFF13\uFF12\uFF16"), arguments("mixed digits", "4\u066326"));
    }

    @Test
    @DisplayName("Collection members use the outer coordinate reference system")
    void whenOnlyANestedCrsUsesNonAsciiDigitsThenTheOuterFactoryIsPreserved() {
        val geometry = json("""
                {"type":"GeometryCollection","geometries":[
                  {"type":"Point","coordinates":[0,0],"crs":{"properties":{"name":"EPSG:\u0664\u0663\u0662\u0666"}}}
                ]}
                """);
        assertThat(isSimple(geometry)).isEqualTo(Value.TRUE);
    }

    private static Value pointWithCrs(String code) {
        val properties = Value.ofObject(Map.of("name", Value.of("EPSG:" + code)));
        val crs        = Value.ofObject(Map.of("properties", properties));
        return Value.ofObject(
                Map.of("type", Value.of("Point"), "coordinates", Value.ofArray(Value.ZERO, Value.ZERO), "crs", crs));
    }

    private static Value isSimple(Value geometry) {
        val broker = new DefaultFunctionBroker();
        broker.load(new GeographicFunctionLibrary());
        return broker.evaluateFunction(new FunctionInvocation("geo.isSimple", List.of(geometry)));
    }
}
