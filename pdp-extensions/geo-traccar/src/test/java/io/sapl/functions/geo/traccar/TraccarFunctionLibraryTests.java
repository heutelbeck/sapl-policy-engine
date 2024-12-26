/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.EXPECTED_GEOFENCE_BUT_GOT_S_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.EXPECTED_POSITION_BUT_GOT_S_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.GEOFENCE_MISSING_AREA_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.GEOMETRY_PROCESSING_ERROR_S_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.NO_VALID_LATITUDE_FIELD_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.NO_VALID_LONGITUDE_FIELD_ERROR;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.traccarGeofenceToGeoJson;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.traccarPositionToGeoJSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary.CoordinateFlippingFilter;

class TraccarFunctionLibraryTests {

    @Test
    void traccarPositionToGeoJSON_withAltitude() throws IOException {
        final var position = Val.ofJson("""
                {
                  "latitude" : 37.7749,
                  "longitude": -122.4194,
                  "altitude" : 100.0
                }
                """);
        final var expected = Val.ofJson("""
                {
                    "type":"Point",
                    "coordinates":[-122.4194,37.7749,100],
                    "crs":{
                        "type":"name",
                        "properties":{
                            "name":"EPSG:4326"
                            }
                        }
                }
                """);
        assertThatVal(traccarPositionToGeoJSON(position)).isEqualTo(expected);
    }

    @Test
    void traccarPositionToGeoJSON_withoutAltitude() throws JsonProcessingException {
        final var position = Val.ofJson("""
                {
                  "latitude" : 37.7749,
                  "longitude": -122.4194
                }
                """);
        final var expected = Val.ofJson("""
                {
                    "type":"Point",
                    "coordinates":[-122.4194,37.7749],
                    "crs":{
                        "type":"name",
                        "properties":{
                            "name":"EPSG:4326"
                            }
                        }
                }
                """);
        assertThatVal(traccarPositionToGeoJSON(position)).isEqualTo(expected);
    }

    @Test
    void traccarPositionToGeoJSON_invalidInput() throws JsonProcessingException {
        final var noLong = Val.ofJson("{}");
        assertThatVal(traccarPositionToGeoJSON(noLong)).isError().contains(NO_VALID_LONGITUDE_FIELD_ERROR);
        final var noLat = Val.ofJson("""
                {
                    "longitude": 12.12
                }
                """);
        assertThatVal(traccarPositionToGeoJSON(noLat)).isError().contains(NO_VALID_LATITUDE_FIELD_ERROR);
        final var undefined = Val.UNDEFINED;
        assertThatVal(traccarPositionToGeoJSON(undefined)).isError()
                .contains(String.format(EXPECTED_POSITION_BUT_GOT_S_ERROR, undefined));
    }

    @Test
    void coordinateFlippingFilterTest() {
        final var filter = new CoordinateFlippingFilter();
        final var coord  = new org.locationtech.jts.geom.Coordinate(10, 20);
        filter.filter(coord);
        assertThat(coord.x).isEqualTo(20, withPrecision(0.001d));
        assertThat(coord.y).isEqualTo(10, withPrecision(0.001d));
    }

    @Test
    void traccarGeofenceToGeoJson_validPolygon() throws JsonProcessingException {
        final var geofence = Val.ofJson("""
                {
                  "area" : "POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))"
                }
                """);
        final var expected = Val.ofJson("""
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
        assertThatVal(traccarGeofenceToGeoJson(geofence)).isEqualTo(expected);
    }

    @Test
    void traccarGeofenceToGeoJson_invalidWKT() throws JsonProcessingException {
        final var geofence = Val.ofJson("""
                {
                  "area" : "invalid WKT"
                }
                """);
        assertThatVal(traccarGeofenceToGeoJson(geofence)).isError()
                .contains(String.format(GEOMETRY_PROCESSING_ERROR_S_ERROR, "Unknown geometry type: INVALID (line 1)"));
    }

    @Test
    void traccarGeofenceToGeoJson_invalidInput() throws JsonProcessingException {
        final var geofence = Val.ofJson("""
                {
                  "invalid" : "data"
                }
                """);
        assertThatVal(traccarGeofenceToGeoJson(geofence)).isError().contains(GEOFENCE_MISSING_AREA_ERROR);
    }

    @Test
    void traccarGeofenceToGeoJson_invalidUndefinedInput() {
        final var geofence = Val.UNDEFINED;
        assertThatVal(traccarGeofenceToGeoJson(geofence)).isError()
                .contains(String.format(EXPECTED_GEOFENCE_BUT_GOT_S_ERROR, geofence));
    }
}
