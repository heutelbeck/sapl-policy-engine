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
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.functions.geo.traccar.TraccarFunctionLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

class TraccarFunctionLibraryTests {

    @Test
    void when_traccarPositionToGeoJSON_withAltitude_then_returnGeoJSONPointWithAltitude() {
        val position = (ObjectValue) json("""
                {
                  "latitude" : 37.7749,
                  "longitude": -122.4194,
                  "altitude" : 100.0
                }
                """);
        val expected = json("""
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
        assertThat(traccarPositionToGeoJSON(position)).isEqualTo(expected);
    }

    @Test
    void when_traccarPositionToGeoJSON_withoutAltitude_then_returnGeoJSONPoint() {
        val position = (ObjectValue) json("""
                {
                  "latitude" : 37.7749,
                  "longitude": -122.4194
                }
                """);
        val expected = json("""
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
        assertThat(traccarPositionToGeoJSON(position)).isEqualTo(expected);
    }

    @Test
    void when_traccarPositionToGeoJSON_missingLongitude_then_returnError() {
        val noLong = (ObjectValue) json("{}");
        var result = traccarPositionToGeoJSON(noLong);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(NO_VALID_LONGITUDE_FIELD_ERROR);
    }

    @Test
    void when_traccarPositionToGeoJSON_missingLatitude_then_returnError() {
        val noLat  = (ObjectValue) json("""
                {
                    "longitude": 12.12
                }
                """);
        var result = traccarPositionToGeoJSON(noLat);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(NO_VALID_LATITUDE_FIELD_ERROR);
    }

    @Test
    void when_coordinateFlippingFilter_then_swapXandY() {
        val filter = new CoordinateFlippingFilter();
        val coord  = new Coordinate(10, 20);
        filter.filter(coord);
        assertThat(coord.x).isEqualTo(20, withPrecision(0.001d));
        assertThat(coord.y).isEqualTo(10, withPrecision(0.001d));
    }

    @Test
    void when_traccarGeofenceToGeoJson_withValidPolygon_then_returnGeoJSONPolygon() {
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
    void when_traccarGeofenceToGeoJson_withInvalidWKT_then_returnError() {
        val geofence = (ObjectValue) json("""
                {
                  "area" : "invalid WKT"
                }
                """);
        var result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message())
                .contains(GEOMETRY_PROCESSING_ERROR_S_ERROR.formatted("Unknown geometry type: INVALID (line 1)"));
    }

    @Test
    void when_traccarGeofenceToGeoJson_missingArea_then_returnError() {
        val geofence = (ObjectValue) json("""
                {
                  "invalid" : "data"
                }
                """);
        var result   = traccarGeofenceToGeoJson(geofence);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(GEOFENCE_MISSING_AREA_ERROR);
    }
}
