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
package io.sapl.functions.geo.traccar;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;

import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("TraccarFunctionLibrary")
class TraccarFunctionLibraryTests {

    private static final int EXCESSIVE_WKT_NESTING_DEPTH = 130;

    static Stream<Arguments> traccarPositionConversionCases() {
        return Stream.of(arguments("""
                { "latitude": 37.7749, "longitude": -122.4194, "altitude": 100.0 }
                """, """
                {"type":"Point","coordinates":[-122.4194,37.7749,100],
                 "crs":{"type":"name","properties":{"name":"EPSG:4326"}}}
                """), arguments("""
                { "latitude": 37.7749, "longitude": -122.4194 }
                """, """
                {"type":"Point","coordinates":[-122.4194,37.7749],
                 "crs":{"type":"name","properties":{"name":"EPSG:4326"}}}
                """), arguments("""
                { "latitude": 37.7749, "longitude": -122.4194, "altitude": "not-a-number" }
                """, """
                {"type":"Point","coordinates":[-122.4194,37.7749],
                 "crs":{"type":"name","properties":{"name":"EPSG:4326"}}}
                """));
    }

    @ParameterizedTest
    @MethodSource("traccarPositionConversionCases")
    void whenTraccarPositionConvertedThenReturnsExpectedGeoJson(String positionJson, String expectedJson) {
        val position = (ObjectValue) json(positionJson);
        val expected = json(expectedJson);
        assertThat(traccarPositionToGeoJSON(position)).isEqualTo(expected);
    }

    @Test
    void whenTraccarPositionMissesLongitudeThenReturnsError() {
        val noLong = (ObjectValue) json("{}");
        val result = traccarPositionToGeoJSON(noLong);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(ERROR_NO_VALID_LONGITUDE_FIELD);
    }

    @Test
    void whenTraccarPositionMissesLatitudeThenReturnsError() {
        val noLat  = (ObjectValue) json("""
                {
                    "longitude": 12.12
                }
                """);
        val result = traccarPositionToGeoJSON(noLat);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(ERROR_NO_VALID_LATITUDE_FIELD);
    }

    @Test
    void whenCoordinateFlippingFilterAppliedThenSwapsXAndY() {
        val filter = new CoordinateFlippingFilter();
        val coord  = new Coordinate(10, 20);
        filter.filter(coord);
        assertThat(coord.x).isEqualTo(20, withPrecision(0.001d));
        assertThat(coord.y).isEqualTo(10, withPrecision(0.001d));
    }

    @Test
    void whenTraccarGeofenceHasValidPolygonThenReturnsGeoJsonPolygon() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))"
                }
                """);
        val expected = json("""
                {
                    "type":"Polygon",
                    "coordinates":[[[10,30],[40,40],[40,20],[20,10],[10,30]]],
                    "crs":{
                        "type":"name",
                        "properties":{
                            "name":"EPSG:4326"
                            }
                        }
                }
                """);
        assertThat(traccarGeofenceToGeoJson(geofence)).isEqualTo(expected);
    }

    @Test
    void whenTraccarGeofenceWktIsInvalidThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "invalid WKT"
                }
                """);
        val result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message())
                .contains(ERROR_GEOMETRY_PROCESSING_S.formatted("Unknown geometry type: INVALID (line 1)"));
    }

    @Test
    void whenTraccarGeofencePolygonIsDegenerateThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "POLYGON ((30 10, 40 40, 20 40))"
                }
                """);
        val result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Error processing geometry:");
    }

    @Test
    void whenTraccarGeofenceAreaIsNotTextThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : 12345
                }
                """);
        val result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(ERROR_GEOFENCE_MISSING_AREA);
    }

    @Test
    void whenTraccarGeofenceAreaIsMissingThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "invalid" : "data"
                }
                """);
        val result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(ERROR_GEOFENCE_MISSING_AREA);
    }

    @Test
    void whenTraccarGeofenceWktExceedsInputLimitThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "%s"
                }
                """.formatted("POINT (1 1)".repeat(420_000)));
        val result   = traccarGeofenceToGeoJson(geofence);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("exceeds the maximum size");
    }

    @Test
    void whenTraccarGeofenceWktNestingExceedsMaximumThenReturnsError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "%s"
                }
                """.formatted(nestedGeometryCollection(EXCESSIVE_WKT_NESTING_DEPTH)));
        val result   = traccarGeofenceToGeoJson(geofence);

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("nesting exceeds"));
    }

    @Test
    void whenTraccarGeofenceWktHasTooManyGeometriesThenReturnsError() {
        val builder = new StringBuilder("MULTIPOINT (");
        for (int i = 0; i < 50_001; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("(1 1)");
        }
        builder.append(')');
        val geofence = (ObjectValue) json("""
                {
                  "area" : "%s"
                }
                """.formatted(builder));
        val result   = traccarGeofenceToGeoJson(geofence);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("exceeds the maximum");
    }

    private static String nestedGeometryCollection(int depth) {
        val builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append("GEOMETRYCOLLECTION(");
        }
        builder.append("POINT (1 1)");
        builder.append(")".repeat(depth));
        return builder.toString();
    }
}
