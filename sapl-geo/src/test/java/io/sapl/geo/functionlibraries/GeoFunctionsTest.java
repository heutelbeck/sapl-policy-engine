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
package io.sapl.geo.functionlibraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import javax.naming.OperationNotSupportedException;
import io.sapl.geo.common.*;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.spatial4j.distance.DistanceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.CrsConst;
import io.sapl.geo.functions.GeometryConverter;

@TestInstance(Lifecycle.PER_CLASS)
class GeoFunctionsTest extends TestBase {

    Val             point1;
    Val             point2;
    Val             point3;
    Val             point4;
    Point           po1;
    Point           po2;
    Point           po3;
    Point           po4;
    Val             polygon1;
    Val             polygon2;
    Val             polygon3;
    Val             polygon4;
    Val             line;
    Val             line1;
    Val             line2;
    Val             line3;
    Val             coll;
    Val             coll1;
    Val             coll2;
    Val             multipoint;
    Val             multipoint2;
    Val             multipoint3;
    Geometry        p1;
    Geometry        p2;
    Geometry        p3;
    GeoFunctions    func    = new GeoFunctions();
    GeometryFactory factory = new GeometryFactory();

    @BeforeAll
    void setup() throws JsonProcessingException {
        point       = Val.of(source.getJsonSource().get("Point"));                                                // (10,12)
        po1         = factory.createPoint(new Coordinate(1, 2));
        point1      = GeometryConverter.geometryToGeoJsonNode(po1);
        po2         = factory.createPoint(new Coordinate(90, 90));
        point2      = GeometryConverter.geometryToGeoJsonNode(po2);
        po3         = factory.createPoint(new Coordinate(150, 150));
        point3      = GeometryConverter.geometryToGeoJsonNode(po3);
        po4         = factory.createPoint(new Coordinate(89.99, 89.99));
        point4      = GeometryConverter.geometryToGeoJsonNode(po4);
        multipoint  = GeometryConverter.geometryToGeoJsonNode(factory.createMultiPoint(new Point[] { po1, po3 }));
        multipoint2 = GeometryConverter.geometryToGeoJsonNode(factory.createMultiPoint(new Point[] { po2, po3 }));
        multipoint3 = GeometryConverter.geometryToGeoJsonNode(factory.createMultiPoint(new Point[] { po1, po2 }));

        polygon = Val.of(source.getJsonSource().get("Polygon")); // ((10 12, 10 14, 12 10, 13 14, 10 12))

        p1 = factory.createPolygon(new Coordinate[] { new Coordinate(100, 100), new Coordinate(100, 200),
                new Coordinate(200, 200), new Coordinate(200, 100), new Coordinate(100, 100), });

        p2       = factory.createLinearRing(new Coordinate[] { new Coordinate(105, 105), new Coordinate(250, 100),
                new Coordinate(100, 200), new Coordinate(250, 200), new Coordinate(105, 105), });
        p3       = factory.createPolygon(new Coordinate[] { new Coordinate(200, 100), new Coordinate(200, 200),
                new Coordinate(300, 200), new Coordinate(300, 100), new Coordinate(200, 100), });
        polygon1 = GeometryConverter.geometryToGeoJsonNode(p1);

        polygon2 = GeometryConverter.geometryToGeoJsonNode(
                factory.createPolygon(new Coordinate[] { new Coordinate(100, 100), new Coordinate(250, 100),
                        new Coordinate(100, 200), new Coordinate(250, 200), new Coordinate(100, 100), }));
        polygon3 = GeometryConverter.geometryToGeoJsonNode(p3);
        polygon4 = GeometryConverter.geometryToGeoJsonNode(
                factory.createPolygon(new Coordinate[] { new Coordinate(105, 105), new Coordinate(250, 100),
                        new Coordinate(100, 200), new Coordinate(250, 200), new Coordinate(105, 105), }));

        line  = GeometryConverter.geometryToGeoJsonNode(
                factory.createLineString(new Coordinate[] { new Coordinate(80, 100), new Coordinate(200, 250) }));
        line1 = GeometryConverter
                .geometryToGeoJsonNode(factory.createLineString(new Coordinate[] { new Coordinate(200, 100),
                        new Coordinate(200, 200), new Coordinate(300, 200), new Coordinate(300, 100) }));
        line2 = GeometryConverter.geometryToGeoJsonNode(
                factory.createLineString(new Coordinate[] { new Coordinate(100, 100), new Coordinate(200, 200) }));
        line3 = GeometryConverter.geometryToGeoJsonNode(
                factory.createLineString(new Coordinate[] { new Coordinate(0, 50), new Coordinate(0, 150) }));
        coll  = GeometryConverter.geometryToGeoJsonNode(factory.createGeometryCollection(new Geometry[] { p1, p3 }));
        coll1 = GeometryConverter.geometryToGeoJsonNode(factory.createGeometryCollection(new Geometry[] { p1 }));

        coll2 = GeometryConverter.geometryToGeoJsonNode(factory.createGeometryCollection(new Geometry[] { po1, po2 }));
    }

    @Test
    void equalsTest() throws ParseException {

        assertTrue(func.geometryEquals(point, point).getBoolean());
        assertTrue(func.geometryEquals(polygon, polygon).getBoolean());
        assertFalse(func.geometryEquals(point, point1).getBoolean());
        assertFalse(func.geometryEquals(polygon, polygon1).getBoolean());

    }

    @Test
    void disjointTest() throws ParseException {

        assertTrue(func.disjoint(point, point1).getBoolean());
        assertTrue(func.disjoint(polygon, polygon1).getBoolean());
        assertFalse(func.disjoint(point, point).getBoolean());
        assertFalse(func.disjoint(polygon, polygon).getBoolean());
    }

    @Test
    void touchesTest() throws ParseException {

        assertTrue(func.touches(point, polygon).getBoolean());
        assertFalse(func.touches(point, polygon1).getBoolean());
    }

    @Test
    void crossesTest() throws ParseException {

        assertTrue(func.crosses(line, polygon1).getBoolean());
        assertFalse(func.crosses(polygon, polygon1).getBoolean());
    }

    @Test
    void withinTest() throws ParseException {

        assertTrue(func.within(line2, polygon1).getBoolean());
        assertTrue(func.within(line2, coll).getBoolean());
        assertFalse(func.within(point2, polygon1).getBoolean());
        assertFalse(func.within(point2, coll).getBoolean());
    }

    @Test
    void containsTest() throws ParseException {

        assertTrue(func.contains(polygon1, line2).getBoolean());
        assertTrue(func.contains(coll, line2).getBoolean());
        assertFalse(func.contains(polygon, point2).getBoolean());
        assertFalse(func.contains(coll, point2).getBoolean());
    }

    @Test
    void overlapsTest() throws ParseException {

        assertTrue(func.overlaps(polygon2, polygon4).getBoolean());
        assertFalse(func.overlaps(polygon, polygon1).getBoolean());
    }

    @Test
    void intersectsTest() throws ParseException {

        assertTrue(func.intersects(polygon1, polygon4).getBoolean());
        assertFalse(func.intersects(polygon, polygon4).getBoolean());
    }

    @Test
    void bufferTest() throws ParseException, JsonProcessingException {

        var expBuffer = GeometryConverter.geometryToGeoJsonNode(po1.buffer(10.0));
        assertEquals(expBuffer, func.buffer(point1, Val.of(10.0)));
    }

    @Test
    void boundaryTest() throws ParseException, JsonProcessingException {

        assertEquals(GeometryConverter.geometryToGeoJsonNode(p2).get().toPrettyString(),
                func.boundary(polygon4).get().toPrettyString());
    }

    @Test
    void centroidTest() throws ParseException, JsonProcessingException {

        assertEquals(point3.get().toPrettyString(), func.centroid(polygon1).get().toPrettyString());
    }

    @Test
    void convexHullTest() throws ParseException, JsonProcessingException {

        assertEquals(GeometryConverter.geometryToGeoJsonNode(p3).get().toPrettyString(),
                func.convexHull(line1).get().toPrettyString());
    }

    @Test
    void unionTest() throws ParseException, JsonProcessingException {

        assertEquals(multipoint.get().toPrettyString(),
                func.union(new Val[] { point1, point3 }).get().toPrettyString());
    }

    @Test
    void unionSingleTest() throws ParseException, JsonProcessingException {

        assertEquals(point1.get().toPrettyString(), func.union(new Val[] { point1 }).get().toPrettyString());
    }

    @Test
    void intersectionTest() throws ParseException, JsonProcessingException {

        assertEquals(GeometryConverter.geometryToGeoJsonNode(factory.createPoint(new Coordinate(200, 200))).get()
                .toPrettyString(), func.intersection(line1, line2).get().toPrettyString());
    }

    @Test
    void differenceTest() throws ParseException, JsonProcessingException {

        assertEquals(line.get().toPrettyString(), func.difference(line, line2).get().toPrettyString());
    }

    @Test
    void symDifferenceTest() throws ParseException, JsonProcessingException {

        assertEquals(multipoint3.get().toPrettyString(),
                func.symDifference(multipoint, multipoint2).get().toPrettyString());
    }

    @Test
    void distanceTest() throws ParseException {

        assertEquals("100.0", func.distance(line2, line3).get().toPrettyString());
    }

    @Test
    void isWithinDistanceTest() throws ParseException {

        assertTrue(func.isWithinDistance(line2, line3, Val.of(110)).get().asBoolean());
        assertFalse(func.isWithinDistance(line2, line3, Val.of(10)).get().asBoolean());
    }

    @Test
    void lenghtTest() throws ParseException {

        assertEquals(100, func.length(line3).get().asDouble());
    }

    @Test
    void areaTest() throws ParseException {

        assertEquals(10000, func.area(polygon1).get().asDouble());
    }

    @Test
    void isSimpleTest() throws ParseException {

        assertTrue(func.isSimple(polygon1).getBoolean());
    }

    @Test
    void isValidTest() throws ParseException {

        assertTrue(func.isValid(polygon1).getBoolean());
    }

    @Test
    void isClosedTest() throws ParseException, OperationNotSupportedException, JsonProcessingException {

        var multiLineString    = """
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
        var multiLineStringVal = Val.ofJson(multiLineString);
        assertTrue(func.isClosed(point1).getBoolean());
        assertTrue(func.isClosed(multipoint).getBoolean());
        assertThrows(OperationNotSupportedException.class, () -> func.isClosed(polygon1));
        assertFalse(func.isClosed(line).getBoolean());
        assertTrue(func.isClosed(multiLineStringVal).getBoolean());
    }

    @Test
    void bagSizeTest() throws ParseException {

        assertEquals(1, func.bagSize(line).get().asInt());
        assertEquals(2, func.bagSize(coll).get().asInt());
    }

    @Test
    void oneAndOnlyTest() throws ParseException, OperationNotSupportedException, JsonProcessingException {

        assertEquals(GeometryConverter.geometryToGeoJsonNode(p1).getText(), func.oneAndOnly(coll1).getText());
        assertThrows(OperationNotSupportedException.class, () -> func.oneAndOnly(coll));
        assertThrows(ClassCastException.class, () -> func.oneAndOnly(polygon));
    }

    @Test
    void geometryIsInTest() throws ParseException {

        assertTrue(func.geometryIsIn(polygon1, coll).getBoolean());
        assertFalse(func.geometryIsIn(polygon2, coll).getBoolean());
        assertThrows(ClassCastException.class, () -> func.geometryIsIn(polygon, polygon1));
    }

    @Test
    void geometryBagTest() throws ParseException, JsonProcessingException {

        assertEquals(coll.get(), func.geometryBag(polygon1, polygon3).get());
    }

    @Test
    void atLeastOneMemberOfTest() {

        assertTrue(func.atLeastOneMemberOf(coll, coll1).getBoolean());
        assertFalse(func.atLeastOneMemberOf(coll, coll2).getBoolean());
    }

    @Test
    void subsetTest() {

        assertTrue(func.subset(coll1, coll).getBoolean());
        assertFalse(func.subset(coll, coll2).getBoolean());
    }

    @Test
    void geoDistanceCrsTest() throws ParseException, FactoryException, TransformException, JsonProcessingException {
        var crs = Val.of(CrsConst.WGS84_CRS.getValue());
        var st  = GeometryConverter.geometryToGeoJsonNode(factory.createPoint(new Coordinate(10.0, 10.0)));
        var de  = GeometryConverter.geometryToGeoJsonNode(factory.createPoint(new Coordinate(10.0, 10.000001)));
        assertTrue(func.geoDistance(st, de, crs).get().asDouble() > (0.1));
    }

    @Test
    void geoDistanceTest() throws ParseException, FactoryException, TransformException, JsonProcessingException {

        var st = GeometryConverter.geometryToGeoJsonNode(factory.createPoint(new Coordinate(10.0, 10.0)));
        var de = GeometryConverter.geometryToGeoJsonNode(factory.createPoint(new Coordinate(10.0, 10.000001)));
        assertTrue(func.geoDistance(st, de).get().asDouble() > (0.1));
    }

    @Test
    void isWithingeoDistanceTest() throws ParseException, TransformException, FactoryException {

        assertTrue(func.isWithinGeoDistance(point2, point4, Val.of(1200)).get().asBoolean());
        assertFalse(func.isWithinGeoDistance(point2, point4, Val.of(2)).get().asBoolean());
    }

    @Test
    void testMilesToMeterJsonNode() throws Exception {
        double miles    = 1.0;
        Val    milesVal = Val.of(miles);
        var    result   = func.milesToMeter(milesVal);
        assertEquals(miles * DistanceUtils.MILES_TO_KM * 1000, result.get().asDouble(), 0.0001);
    }

    @Test
    void testYardToMeter() throws Exception {
        double yards          = 1.0;
        Val    yardVal        = Val.of(yards);
        var    result         = func.yardToMeter(yardVal);
        var    expectedMeters = (yards / 1760) * DistanceUtils.MILES_TO_KM * 1000;
        assertEquals(expectedMeters, result.get().asDouble(), 0.0001);
    }

    @Test
    void testDegreeToMeter() {

        Val inputVal = Val.of(1.0);
        Val result   = func.degreeToMeter(inputVal);
        assertEquals(111195.07973436874, result.get().asDouble(), 0.0001);
    }
}
