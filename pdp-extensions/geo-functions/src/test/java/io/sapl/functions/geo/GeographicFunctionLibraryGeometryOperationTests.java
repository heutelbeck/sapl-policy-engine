/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.spatial4j.distance.DistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import lombok.SneakyThrows;

class GeographicFunctionLibraryGeometryOperationTests {

    private static final String WGS84 = "EPSG:4326";

    private static final GeoJsonWriter   GEOJSON_WRITER = new GeoJsonWriter();
    private static final GeometryFactory GEO_FACTORY    = new GeometryFactory();

    // @formatter:off
    private static final Point POINT_1_2_GEOMETRY         = GEO_FACTORY.createPoint(new Coordinate(1    , 2    ));
    private static final Point POINT_10_12_GEOMETRY       = GEO_FACTORY.createPoint(new Coordinate(10   , 12   ));
    private static final Point POINT_90_90_GEOMETRY       = GEO_FACTORY.createPoint(new Coordinate(90   , 90   ));
    private static final Point POINT_150_150_GEOMETRY     = GEO_FACTORY.createPoint(new Coordinate(150  , 150  ));
    private static final Point POINT_89_99_89_99_GEOMETRY = GEO_FACTORY.createPoint(new Coordinate(89.99, 89.99));

    private static final Val POINT_1_2         = geometryToGeoJSON(POINT_1_2_GEOMETRY);
    private static final Val POINT_10_12       = geometryToGeoJSON(POINT_10_12_GEOMETRY);
    private static final Val POINT_90_90       = geometryToGeoJSON(POINT_90_90_GEOMETRY);
    private static final Val POINT_150_150     = geometryToGeoJSON(POINT_150_150_GEOMETRY);
    private static final Val POINT_89_99_89_99 = geometryToGeoJSON(POINT_89_99_89_99_GEOMETRY);

    private static final Val MULTIPOINT_1_2_150_150   = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] { 
                                                                                    POINT_1_2_GEOMETRY, 
                                                                                    POINT_150_150_GEOMETRY }));
    
    private static final Val MULTIPOINT_90_90_150_150 = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] { 
                                                                                    POINT_90_90_GEOMETRY, 
                                                                                    POINT_150_150_GEOMETRY }));

    private static final Val MULTIPOINT_1_2_90_90     = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] {
                                                                                    POINT_1_2_GEOMETRY, 
                                                                                    POINT_90_90_GEOMETRY }));

    private static final Geometry POLYGON_1_GEOMETRY = GEO_FACTORY.createPolygon(new Coordinate[] { 
                                                                                    new Coordinate(100, 100), 
                                                                                    new Coordinate(100, 200),
                                                                                    new Coordinate(200, 200), 
                                                                                    new Coordinate(200, 100), 
                                                                                    new Coordinate(100, 100), });
    
    private static final Geometry POLYGON_2_GEOMETRY = GEO_FACTORY.createLinearRing(new Coordinate[] { 
                                                                                    new Coordinate(105, 105),
                                                                                    new Coordinate(250, 100),
                                                                                    new Coordinate(100, 200),
                                                                                    new Coordinate(250, 200),
                                                                                    new Coordinate(105, 105), });

    private static final Geometry POLYGON_3_GEOMETRY = GEO_FACTORY.createPolygon(new Coordinate[] { 
                                                                                    new Coordinate(200, 100),
                                                                                    new Coordinate(200, 200),
                                                                                    new Coordinate(300, 200), 
                                                                                    new Coordinate(300, 100), 
                                                                                    new Coordinate(200, 100), });
    
    private static final Geometry POLYGON_4_GEOMETRY = GEO_FACTORY.createPolygon(new Coordinate[] { 
                                                                                    new Coordinate(10, 12), 
                                                                                    new Coordinate(10, 14),
                                                                                    new Coordinate(12, 10), 
                                                                                    new Coordinate(13, 14), 
                                                                                    new Coordinate(10, 12), });
    
    private static final Geometry POLYGON_5_GEOMETRY =   GEO_FACTORY.createPolygon(new Coordinate[] { 
                                                                                    new Coordinate(105, 105),
                                                                                    new Coordinate(250, 100),
                                                                                    new Coordinate(100, 200),
                                                                                    new Coordinate(250, 200),
                                                                                    new Coordinate(105, 105), });
    
    private static final Geometry POLYGON_6_GEOMETRY = GEO_FACTORY.createPolygon(new Coordinate[] { 
                                                                                    new Coordinate(100, 100), 
                                                                                    new Coordinate(250, 100),
                                                                                    new Coordinate(100, 200), 
                                                                                    new Coordinate(250, 200), 
                                                                                    new Coordinate(100, 100), });

    
    private static final Val POLYGON_1 = geometryToGeoJSON(POLYGON_1_GEOMETRY);
    private static final Val POLYGON_3 = geometryToGeoJSON(POLYGON_3_GEOMETRY);
    private static final Val POLYGON_4 = geometryToGeoJSON(POLYGON_4_GEOMETRY);
    private static final Val POLYGON_5 = geometryToGeoJSON(POLYGON_5_GEOMETRY);
    private static final Val POLYGON_6 = geometryToGeoJSON(POLYGON_6_GEOMETRY);
    
    private static final Val SELF_INTERSECTING_POLYGON = geometryToGeoJSON(GEO_FACTORY.createPolygon(
                                                                           GEO_FACTORY.createLinearRing(new Coordinate[] { 
                                                                                    new Coordinate(0, 0),
                                                                                    new Coordinate(4, 4), 
                                                                                    new Coordinate(4, 0), 
                                                                                    new Coordinate(0, 4), 
                                                                                    new Coordinate(0, 0) }), null));

    private static final Val LINE_1 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] { 
                                                                                    new Coordinate(80, 100),
                                                                                    new Coordinate(200, 250) }));
    
    private static final Val LINE_2 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] {
                                                                                    new Coordinate(200, 100), 
                                                                                    new Coordinate(200, 200), 
                                                                                    new Coordinate(300, 200), 
                                                                                    new Coordinate(300, 100) }));
    
    private static final Val LINE_3 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] { 
                                                                                    new Coordinate(100, 100), 
                                                                                    new Coordinate(200, 200) }));
    
    private static final Val LINE_4 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] { 
                                                                                    new Coordinate(0, 50),
                                                                                    new Coordinate(0, 150) }));
    
    private static final Val COLLECTION_1 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] { 
                                                                                    POLYGON_1_GEOMETRY, 
                                                                                    POLYGON_3_GEOMETRY }));
    
    private static final Val COLLECTION_2 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] { 
                                                                                    POLYGON_1_GEOMETRY }));
    
    private static final Val COLLECTION_3 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] { 
                                                                                    POINT_1_2_GEOMETRY,
                                                                                    POINT_90_90_GEOMETRY }));
    // @formatter:on

    @Test
    void equalsTest() {
        assertTrue(GeographicFunctionLibrary.equalsExact(POINT_10_12, POINT_10_12).getBoolean());
        assertTrue(GeographicFunctionLibrary.equalsExact(POLYGON_4, POLYGON_4).getBoolean());
        assertFalse(GeographicFunctionLibrary.equalsExact(POINT_10_12, POINT_10_12).getBoolean());
        assertFalse(GeographicFunctionLibrary.equalsExact(POLYGON_4, POLYGON_1).getBoolean());
    }

    @Test
    void disjointTest() {
        assertTrue(GeographicFunctionLibrary.disjoint(POINT_10_12, POINT_10_12).getBoolean());
        assertTrue(GeographicFunctionLibrary.disjoint(POLYGON_4, POLYGON_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.disjoint(POINT_10_12, POINT_10_12).getBoolean());
        assertFalse(GeographicFunctionLibrary.disjoint(POLYGON_4, POLYGON_4).getBoolean());
    }

    @Test
    void touchesTest() {
        assertTrue(GeographicFunctionLibrary.touches(POINT_10_12, POLYGON_4).getBoolean());
        assertFalse(GeographicFunctionLibrary.touches(POINT_10_12, POLYGON_1).getBoolean());
    }

    @Test
    void crossesTest() {
        assertTrue(GeographicFunctionLibrary.crosses(LINE_1, POLYGON_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.crosses(POLYGON_4, POLYGON_1).getBoolean());
    }

    @Test
    void withinTest() {
        assertTrue(GeographicFunctionLibrary.within(LINE_3, POLYGON_1).getBoolean());
        assertTrue(GeographicFunctionLibrary.within(LINE_3, COLLECTION_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.within(POINT_90_90, POLYGON_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.within(POINT_90_90, COLLECTION_1).getBoolean());
    }

    @Test
    void containsTest() {
        assertTrue(GeographicFunctionLibrary.contains(POLYGON_1, LINE_3).getBoolean());
        assertTrue(GeographicFunctionLibrary.contains(COLLECTION_1, LINE_3).getBoolean());
        assertFalse(GeographicFunctionLibrary.contains(POLYGON_4, POINT_90_90).getBoolean());
        assertFalse(GeographicFunctionLibrary.contains(COLLECTION_1, POINT_90_90).getBoolean());
    }

    @Test
    void overlapsTest() {
        assertTrue(GeographicFunctionLibrary.overlaps(POLYGON_6, POLYGON_5).getBoolean());
        assertFalse(GeographicFunctionLibrary.overlaps(POLYGON_4, POLYGON_1).getBoolean());
    }

    @Test
    void intersectsTest() {
        assertTrue(GeographicFunctionLibrary.intersects(POLYGON_1, POLYGON_5).getBoolean());
        assertFalse(GeographicFunctionLibrary.intersects(POLYGON_4, POLYGON_5).getBoolean());
    }

    @Test
    void bufferTest() {
        final var expBuffer = geometryToGeoJSON(POINT_1_2_GEOMETRY.buffer(10.0));
        assertEquals(expBuffer, GeographicFunctionLibrary.buffer(POINT_1_2, Val.of(10.0)));
    }

    @Test
    void boundaryTest() {
        assertEquals(geometryToGeoJSON(POLYGON_2_GEOMETRY).get(), GeographicFunctionLibrary.boundary(POLYGON_5).get());
    }

    @Test
    void centroidTest() {
        assertEquals(POINT_150_150.get(), GeographicFunctionLibrary.centroid(POLYGON_1).get());
    }

    @Test
    void convexHullTest() {
        assertEquals(geometryToGeoJSON(POLYGON_3_GEOMETRY).get(), GeographicFunctionLibrary.convexHull(LINE_2).get());
    }

    @Test
    void unionTest() {
        assertEquals(MULTIPOINT_1_2_150_150.get(),
                GeographicFunctionLibrary.union(new Val[] { POINT_10_12, POINT_150_150 }).get());
    }

    @Test
    void unionSingleTest() {
        assertEquals(POINT_10_12.get(), GeographicFunctionLibrary.union(new Val[] { POINT_10_12 }).get());
    }

    @Test
    void intersectionTest() {
        assertEquals(geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(200, 200))).get(),
                GeographicFunctionLibrary.intersection(LINE_2, LINE_3).get());
    }

    @Test
    void differenceTest() {
        assertEquals(LINE_1.get(), GeographicFunctionLibrary.difference(LINE_1, LINE_3).get());
    }

    @Test
    void symDifferenceTest() {
        assertEquals(MULTIPOINT_1_2_90_90.get(),
                GeographicFunctionLibrary.symDifference(MULTIPOINT_1_2_150_150, MULTIPOINT_90_90_150_150).get());
    }

    @Test
    void distanceTest() {
        assertEquals("100.0", GeographicFunctionLibrary.distance(LINE_3, LINE_4).get());
    }

    @Test
    void isWithinDistanceTest() {
        assertTrue(GeographicFunctionLibrary.isWithinDistance(LINE_3, LINE_4, Val.of(110)).get().asBoolean());
        assertFalse(GeographicFunctionLibrary.isWithinDistance(LINE_3, LINE_4, Val.of(10)).get().asBoolean());
    }

    @Test
    void lenghtTest() {
        assertEquals(100, GeographicFunctionLibrary.length(LINE_4).get().asDouble());
    }

    @Test
    void areaTest() {
        assertEquals(10000, GeographicFunctionLibrary.area(POLYGON_1).get().asDouble());
    }

    @Test
    void isSimpleTest() {
        assertFalse(GeographicFunctionLibrary.isSimple(SELF_INTERSECTING_POLYGON).getBoolean());
        assertTrue(GeographicFunctionLibrary.isSimple(POLYGON_1).getBoolean());
    }

    @Test
    void isValidTest() {
        assertFalse(GeographicFunctionLibrary.isValid(SELF_INTERSECTING_POLYGON).getBoolean());
        assertTrue(GeographicFunctionLibrary.isValid(POLYGON_1).getBoolean());
    }

    @Test
    void isClosedTest() throws JsonProcessingException {
        final var multiLineString    = """
                {
                    "type": "MultiLineString",
                    "coordinates": [
                        [
                            [0.0, 0.0],
                            [1.0, 1.0],
                            [1.0, 0.0],
                            [0.0, 0.0]
                        ],
                        [
                            [2.0, 2.0],
                            [3.0, 3.0],
                            [3.0, 2.0],
                            [2.0, 2.0]
                        ]
                    ]
                }
                """;
        final var multiLineStringVal = Val.ofJson(multiLineString);
        assertTrue(GeographicFunctionLibrary.isClosed(POINT_10_12).getBoolean());
        assertTrue(GeographicFunctionLibrary.isClosed(MULTIPOINT_1_2_150_150).getBoolean());
        assertThrows(OperationNotSupportedException.class, () -> GeographicFunctionLibrary.isClosed(POLYGON_1));
        assertFalse(GeographicFunctionLibrary.isClosed(LINE_1).getBoolean());
        assertTrue(GeographicFunctionLibrary.isClosed(multiLineStringVal).getBoolean());
    }

    @Test
    void bagSizeTest() {
        assertEquals(1, GeographicFunctionLibrary.bagSize(LINE_1).get().asInt());
        assertEquals(2, GeographicFunctionLibrary.bagSize(COLLECTION_1).get().asInt());
    }

    @Test
    void oneAndOnlyTest() {
        assertEquals(geometryToGeoJSON(POLYGON_1_GEOMETRY).getText(),
                GeographicFunctionLibrary.oneAndOnly(COLLECTION_2).getText());
        assertThrows(OperationNotSupportedException.class, () -> GeographicFunctionLibrary.oneAndOnly(COLLECTION_1));
        assertThrows(ClassCastException.class, () -> GeographicFunctionLibrary.oneAndOnly(POLYGON_4));
    }

    @Test
    void geometryIsInTest() {
        assertTrue(GeographicFunctionLibrary.geometryIsIn(POLYGON_1, COLLECTION_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.geometryIsIn(POLYGON_6, COLLECTION_1).getBoolean());
        assertThrows(ClassCastException.class, () -> GeographicFunctionLibrary.geometryIsIn(POLYGON_4, POLYGON_1));
    }

    @Test
    void geometryBagTest() {
        assertEquals(COLLECTION_1.get(), GeographicFunctionLibrary.geometryBag(POLYGON_1, POLYGON_3).get());
    }

    @Test
    void atLeastOneMemberOfTest() {
        assertTrue(GeographicFunctionLibrary.atLeastOneMemberOf(COLLECTION_1, COLLECTION_2).getBoolean());
        assertFalse(GeographicFunctionLibrary.atLeastOneMemberOf(COLLECTION_1, COLLECTION_3).getBoolean());
    }

    @Test
    void subsetTest() {
        assertTrue(GeographicFunctionLibrary.subset(COLLECTION_2, COLLECTION_1).getBoolean());
        assertFalse(GeographicFunctionLibrary.subset(COLLECTION_1, COLLECTION_3).getBoolean());
    }

    @Test
    void geoDistanceCrsTest() {
        final var crs = Val.of(WGS84);
        final var st  = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        final var de  = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        assertTrue(GeographicFunctionLibrary.geoDistance(st, de, crs).get().asDouble() > (0.1));
    }

    @Test
    void geoDistanceTest() {
        final var st = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        final var de = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        assertTrue(GeographicFunctionLibrary.geoDistance(st, de).get().asDouble() > (0.1));
    }

    @Test
    void isWithingeoDistanceTest() {
        assertTrue(GeographicFunctionLibrary.isWithinGeoDistance(POINT_90_90, POINT_89_99_89_99, Val.of(1200)).get()
                .asBoolean());
        assertFalse(GeographicFunctionLibrary.isWithinGeoDistance(POINT_90_90, POINT_89_99_89_99, Val.of(2)).get()
                .asBoolean());
    }

    @Test
    void testMilesToMeterJsonNode() {
        final var miles    = 1.0;
        final var milesVal = Val.of(miles);
        final var result   = GeographicFunctionLibrary.milesToMeter(milesVal);
        assertEquals(miles * DistanceUtils.MILES_TO_KM * 1000, result.get().asDouble(), 0.0001);
    }

    @Test
    void testYardToMeter() {
        final var yards          = 1.0;
        final var yardVal        = Val.of(yards);
        final var result         = GeographicFunctionLibrary.yardToMeter(yardVal);
        final var expectedMeters = (yards / 1760) * DistanceUtils.MILES_TO_KM * 1000;
        assertEquals(expectedMeters, result.get().asDouble(), 0.0001);
    }

    @Test
    void testDegreeToMeter() {
        final var inputVal = Val.of(1.0);
        final var result   = GeographicFunctionLibrary.degreeToMeter(inputVal);
        assertEquals(111195.07973436874, result.get().asDouble(), 0.0001);
    }

    @SneakyThrows
    private static Val geometryToGeoJSON(Geometry geo) {
        return Val.ofJson(GEOJSON_WRITER.write(geo));
    }
}
