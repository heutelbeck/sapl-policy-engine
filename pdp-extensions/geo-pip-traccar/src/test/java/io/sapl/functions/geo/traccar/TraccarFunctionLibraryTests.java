package io.sapl.functions.geo.traccar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.pip.geo.traccar.TraccarSchemata;

class TraccarFunctionLibraryTests {

    private static final ObjectMapper    MAPPER = new ObjectMapper();
    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;

    @Test
    void traccarPositionToGeoJSON_withAltitude() throws IOException {
        // Arrange
        final var position           = JSON.objectNode().put(TraccarSchemata.LATITUDE, 37.7749)
                .put(TraccarSchemata.LONGITUDE, -122.4194).put(TraccarSchemata.ALTITUDE, 100.0);
        final var positionVal        = Val.of(position);
        final var expectedJsonString = """
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
                """;
        final var expectedJson       = MAPPER.readTree(expectedJsonString);

        // Act
        final var result     = TraccarFunctionLibrary.traccarPositionToGeoJSON(positionVal);
        final var resultJson = MAPPER.readTree(result.getText());
        // Assert
        assertNotNull(result);
        assertEquals(expectedJson, resultJson);
    }

    @Test
    void traccarPositionToGeoJSON_withoutAltitude() throws IOException {
        // Arrange
        final var position    = JSON.objectNode().put(TraccarSchemata.LATITUDE, 37.7749).put(TraccarSchemata.LONGITUDE,
                -122.4194);
        final var positionVal = Val.of(position);

        final var expectedJsonString = """
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
                """;
        final var expectedJson       = MAPPER.readTree(expectedJsonString);

        // Act
        final var result     = TraccarFunctionLibrary.traccarPositionToGeoJSON(positionVal);
        final var resultJson = MAPPER.readTree(result.getText());

        // Assert
        assertNotNull(result);
        assertEquals(expectedJson, resultJson);
    }

    @Test
    void traccarPositionToGeoJSON_invalidInput() {
        // Arrange
        final var invalidPositionVal = Val.of(JSON.objectNode().put("invalid", "data"));

        // Act & Assert
        try {
            TraccarFunctionLibrary.traccarPositionToGeoJSON(invalidPositionVal);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    void coordinateFlippingFilterTest() {
        // Arrange
        final var filter = new TraccarFunctionLibrary.CoordinateFlippingFilter();
        final var coord  = new org.locationtech.jts.geom.Coordinate(10, 20);

        // Act
        filter.filter(coord);
        // Assert
        assertEquals(20, coord.x);
        assertEquals(10, coord.y);
    }

    @Test
    void traccarGeofenceToGeoJson_validPolygon() throws IOException {
        // Arrange
        final var wktPolygon  = "POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))";
        final var geofence    = JSON.objectNode().put(TraccarSchemata.AREA, wktPolygon);
        final var geofenceVal = Val.of(geofence);

        final var expectedJsonString = """
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
                """;
        final var expectedJson       = MAPPER.readTree(expectedJsonString);

        // Act
        final var result     = TraccarFunctionLibrary.traccarGeofenceToGeoJson(geofenceVal);
        final var resultJson = MAPPER.readTree(result.getText());

        // Assert
        assertNotNull(result);
        assertEquals(expectedJson, resultJson);
    }

    @Test
    void traccarGeofenceToGeoJson_invalidWKT() {
        // Arrange
        final var geofence    = JSON.objectNode().put(TraccarSchemata.AREA, "invalid WKT");
        final var geofenceVal = Val.of(geofence);

        // Act & Assert
        try {
            TraccarFunctionLibrary.traccarGeofenceToGeoJson(geofenceVal);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    void traccarGeofenceToGeoJson_invalidInput() {
        // Arrange
        final var geofenceVal = Val.of(JSON.objectNode().put("invalid", "data"));

        // Act & Assert
        try {
            TraccarFunctionLibrary.traccarGeofenceToGeoJson(geofenceVal);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
}
