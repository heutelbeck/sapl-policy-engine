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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.pip.geo.traccar.TraccarSchemata;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = "traccar", description = TraccarFunctionLibrary.DESCRIPTION)
public class TraccarFunctionLibrary {

    static final String DESCRIPTION = "An utility function library for extracting geometries from Traccar positions and geofences.";

    private static final int             WGS84                  = 4326;
    private static final GeoJsonWriter   GEOJSON_WRITER         = new GeoJsonWriter();
    private static final GeometryFactory WGS84_GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    static final String GEOMETRY_PROCESSING_ERROR_S_ERROR = "Error processing geometry: %s";
    static final String GEOFENCE_MISSING_AREA_ERROR       = "The geofence object missing the 'area' field.";
    static final String EXPECTED_GEOFENCE_BUT_GOT_S_ERROR = "Expected a Traccar geofence object, but got: %s";
    static final String NO_VALID_LATITUDE_FIELD_ERROR     = "Expected a Traccar position object, but there is no valid latitude field.";
    static final String NO_VALID_LONGITUDE_FIELD_ERROR    = "Expected a Traccar position object, but there is no valid longitude field.";
    static final String EXPECTED_POSITION_BUT_GOT_S_ERROR = "Expected a Traccar position object, but got %s";

    @Function(docs = """
            ```traccarPositionToGeoJSON(OBJECT traccarPosition)```: Converts a Traccar position object to a GeoJSON string
            representing a Point.
            The function expects a Traccar position object as input, which must contain at least the `latitude` and
            `longitude` fields.
            If the position object also contains an `altitude` field, this will be included in the GeoJSON output.
            The output GeoJSON will also include the WGS84 CRS (Coordinate Reference System) as "EPSG:4326".

            **Example:**

            ```
            import traccar.*
            policy "example"
            permit
            where
                var position = {
                    "id": 123,
                    "deviceId": 456,
                    "type": "position",
                    "protocol": "h02",
                    "latitude": 37.7749,
                    "longitude": -122.4194,
                    "altitude": 100.0,
                    "speed": 0.0,
                    "course": 0.0,
                    "accuracy": 5.0,
                    "timestamp": "2024-03-08T12:00:00Z",
                    "serverTime": "2024-03-08T12:00:05Z",
                    "valid": true,
                    "attributes": {
                      "battery": 95.5,
                      "motion": false
                      }
                };
                traccarPositionToGeoJSON(position) == '{"type":"Point","coordinates":[-122.4194,37.7749,100.0],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}}';
            ```
            """)
    public static Val traccarPositionToGeoJSON(Val traccarPosition) {
        if (!traccarPosition.isDefined()) {
            return Val.error(String.format(EXPECTED_POSITION_BUT_GOT_S_ERROR, traccarPosition));
        }
        final var positionJson  = traccarPosition.get();
        final var longitudeNode = positionJson.get(TraccarSchemata.LONGITUDE);
        if (longitudeNode == null || !longitudeNode.isNumber()) {
            return Val.error(NO_VALID_LONGITUDE_FIELD_ERROR);
        }
        final var longitude    = longitudeNode.asDouble();
        final var latitudeNode = positionJson.get(TraccarSchemata.LATITUDE);
        if (latitudeNode == null || !latitudeNode.isNumber()) {
            return Val.error(NO_VALID_LATITUDE_FIELD_ERROR);
        }
        final var latitude = latitudeNode.asDouble();
        Geometry  geometry;
        if (positionJson.has(TraccarSchemata.ALTITUDE)) {
            final var altitude = positionJson.get(TraccarSchemata.ALTITUDE).asDouble();
            geometry = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude, altitude));
        } else {
            geometry = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        }
        try {
            return Val.ofJson(GEOJSON_WRITER.write(geometry));
        } catch (JsonProcessingException e) {
            return Val.error(String.format(GEOMETRY_PROCESSING_ERROR_S_ERROR, e.getMessage()));
        }
    }

    public static class CoordinateFlippingFilter implements CoordinateFilter {
        public void filter(Coordinate coord) {
            double oldX = coord.x;
            coord.x = coord.y;
            coord.y = oldX;
        }
    }

    @Function(docs = """
            ```traccarGeofenceToGeoJson(OBJECT geofence)```: Converts a Traccar geofence object to a GeoJSON string
            representing the geofence's geometry.
            The function expects a Traccar geofence object as input, which must contain an `area` field. The `area` field
            represents the geofence's geometry in Well-Known Text (WKT) format.
            The function will flip the coordinates within the WKT to match the GeoJSON convention of [longitude, latitude].
            The output GeoJSON will also include the WGS84 CRS (Coordinate Reference System) as "EPSG:4326".

            **Example:**

            ```
            import traccar.*
            policy "example"
            permit
            where
                 var geofence = {
                     "id": 789,
                     "name": "Test Geofence",
                     "calendarId": 1,
                     "description": "A test geofence",
                     "area": "POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))",
                     "attributes": {
                          "type": "polygon"
                      }
                   };
                 traccarGeofenceToGeoJson(geofence) == '{"type":"Polygon","coordinates":[[[10.0,30.0],[40.0,40.0],[40.0,20.0],[20.0,10.0],[10.0,30.0]]],"crs":{"type":"name","properties":{"name":"EPSG:4326"}}}';
            ```
             """)
    public static Val traccarGeofenceToGeoJson(Val geofence) {
        if (!geofence.isDefined()) {
            return Val.error(String.format(EXPECTED_GEOFENCE_BUT_GOT_S_ERROR, geofence));
        }
        final var area = geofence.get().get(TraccarSchemata.AREA);
        if (area == null) {
            return Val.error(GEOFENCE_MISSING_AREA_ERROR);
        }
        try {
            final var geometry = new WKTReader().read(area.asText());
            geometry.setSRID(WGS84);
            // GeoJSON needs coordinates in longitude then latitude. Geometry will have it
            // the other way around.
            geometry.apply(new CoordinateFlippingFilter());
            return Val.ofJson(GEOJSON_WRITER.write(geometry));
        } catch (ParseException | JsonProcessingException e) {
            return Val.error(String.format(GEOMETRY_PROCESSING_ERROR_S_ERROR, e.getMessage()));
        }
    }

}
