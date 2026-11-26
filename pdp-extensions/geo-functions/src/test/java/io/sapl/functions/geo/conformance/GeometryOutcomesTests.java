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
package io.sapl.functions.geo.conformance;

import static io.sapl.api.model.ValueJsonMarshaller.toJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.val;

@Tag("conformance")
@DisplayName("Geometry corpus point-set dimensions")
class GeometryOutcomesTests {

    static Stream<Arguments> pointSets() {
        val point        = "{\"type\":\"Point\",\"coordinates\":[1,1]}";
        val emptyPolygon = "{\"type\":\"Polygon\",\"coordinates\":[]}";
        val collection   = "{\"type\":\"GeometryCollection\",\"geometries\":[" + point + "," + emptyPolygon + "]}";
        val nested       = "{\"type\":\"GeometryCollection\",\"geometries\":[" + collection
                + ",{\"type\":\"LineString\",\"coordinates\":[]}]}";
        val line         = "{\"type\":\"LineString\",\"coordinates\":[[1,1],[2,1]]}";
        return Stream.of(arguments("empty polygon does not raise dimension", collection, point, 0, true),
                arguments("nested empty members do not raise dimension", nested, point, 0, true),
                arguments("same ordinary point", point, point, 0, true),
                arguments("different ordinary point", point, "{\"type\":\"Point\",\"coordinates\":[2,1]}", 0, false),
                arguments("nonempty line still raises dimension within distance tolerance", line, point, 1, false),
                arguments("empty point sets", emptyPolygon, "{\"type\":\"GeometryCollection\",\"geometries\":[]}", 0,
                        true))
                .flatMap(row -> {
                    val values = row.get();
                    return Stream.of(row,
                            arguments(values[0] + " reversed", values[2], values[1], values[3], values[4]));
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pointSets")
    @DisplayName("Empty members contribute no dimension to a tolerance comparison")
    void whenPointSetsAreComparedThenOnlyNonemptyMembersDetermineDimension(String description, String stated,
            String returned, int tolerance, boolean expected) {
        val document     = "json.jsonToVal\n" + toJsonString(Value.of(returned));
        val functionCase = new Case("point-set-dimensions.cases", description, document, document,
                "geometry within " + tolerance + ": " + stated);
        assertThat(GeometryOutcomes.agrees(functionCase)).as(description).isEqualTo(expected);
    }
}
