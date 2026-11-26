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

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;

@Timeout(10)
@DisplayName("Geographic XML coordinate separators")
class GeometryDocumentsTests {

    private enum XmlFormat {
        KML,
        GML2,
        GML3
    }

    private record CoordinateWhitespace(String description, String coordinates) {}

    static Stream<Arguments> coordinateDocuments() {
        val spacing = List.of(new CoordinateWhitespace("plain", "1,2 3,4"),
                new CoordinateWhitespace("spaces around commas", " 1 , 2  3 , 4 "),
                new CoordinateWhitespace("XML whitespace", "\t1\n,\t2\r\n3,\n4\t"),
                new CoordinateWhitespace("long gap between tuples", "1,2" + " ".repeat(32768) + "3,4"),
                new CoordinateWhitespace("long trailing gap", "1,2 3,4" + " ".repeat(32768)));
        return Arrays.stream(XmlFormat.values()).flatMap(
                format -> spacing.stream().map(entry -> arguments(format, entry.description(), entry.coordinates())));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("coordinateDocuments")
    @DisplayName("Coordinate whitespace preserves the positions and separates tuples")
    void whenCoordinateSeparatorsContainWhitespaceThenThePositionsArePreserved(XmlFormat format, String description,
            String coordinates) {
        val namespace = format == XmlFormat.KML ? "http://www.opengis.net/kml/2.2" : "http://www.opengis.net/gml";
        val document  = """
                <LineString xmlns="%s"><coordinates>%s</coordinates></LineString>""".formatted(namespace, coordinates);
        val input     = Value.of(document);
        val result    = switch (format) {
                      case KML  -> GeographicFunctionLibrary.kmlToGeoJSON(input);
                      case GML2 -> GeographicFunctionLibrary.gml2ToGeoJSON(input);
                      case GML3 -> GeographicFunctionLibrary.gml3ToGeoJSON(input);
                      };
        assertThat(result).isEqualTo(json("""
                {"type":"LineString","coordinates":[[1,2],[3,4]]}
                """));
    }

    static Stream<Arguments> leadingMarkers() {
        return Arrays.stream(XmlFormat.values())
                .flatMap(format -> Stream.of(arguments(format, "plain", "", true),
                        arguments(format, "one leading mark", "\uFEFF", true),
                        arguments(format, "two leading marks", "\uFEFF\uFEFF", false)));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("leadingMarkers")
    @DisplayName("Decoded geographic XML text accepts exactly one optional leading byte order mark")
    void whenGeographicMarkupStartsWithByteOrderMarkThenExactlyOneIsAllowed(XmlFormat format, String description,
            String prefix, boolean accepted) {
        val namespace = format == XmlFormat.KML ? "http://www.opengis.net/kml/2.2" : "http://www.opengis.net/gml";
        val document  = "<Point xmlns=\"%s\"><coordinates>1,2</coordinates></Point>".formatted(namespace);
        val input     = Value.of(prefix + document);
        val result    = switch (format) {
                      case KML  -> GeographicFunctionLibrary.kmlToGeoJSON(input);
                      case GML2 -> GeographicFunctionLibrary.gml2ToGeoJSON(input);
                      case GML3 -> GeographicFunctionLibrary.gml3ToGeoJSON(input);
                      };
        val actual    = accepted ? result : Value.of(result instanceof ErrorValue);
        val expected  = accepted ? json("{\"type\":\"Point\",\"coordinates\":[1,2]}") : Value.TRUE;
        assertThat(actual).isEqualTo(expected);
    }

}
