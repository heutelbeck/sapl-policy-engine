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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.SneakyThrows;
import lombok.val;
import org.geotools.api.referencing.FactoryException;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.spatial4j.distance.DistanceUtils;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.functions.geo.GeographicFunctionLibrary.*;
import static io.sapl.functions.geo.GeographicFunctionLibrary.within;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

class GeographicFunctionLibraryTests {

    private static final String WGS84 = "EPSG:4326";

    private static final GeoJsonWriter   GEOJSON_WRITER = new GeoJsonWriter();
    private static final GeometryFactory GEO_FACTORY    = new GeometryFactory();
    private static final GeometryFactory WGS84_FACTORY  = new GeometryFactory(new PrecisionModel(), 4326);

    // @formatter:off
    private static final Point POINT_1_2_GEOMETRY         = GEO_FACTORY.createPoint(new Coordinate(1    , 2    ));
    private static final Point POINT_10_12_GEOMETRY       = GEO_FACTORY.createPoint(new Coordinate(10   , 12   ));
    private static final Point POINT_90_90_GEOMETRY       = GEO_FACTORY.createPoint(new Coordinate(90   , 90   ));
    private static final Point POINT_150_150_GEOMETRY     = GEO_FACTORY.createPoint(new Coordinate(150  , 150  ));
    private static final Point POINT_89_99_89_99_GEOMETRY = GEO_FACTORY.createPoint(new Coordinate(89.99, 89.99));

    private static final ObjectValue POINT_1_2         = geometryToGeoJSON(POINT_1_2_GEOMETRY);
    private static final ObjectValue POINT_10_12       = geometryToGeoJSON(POINT_10_12_GEOMETRY);
    private static final ObjectValue POINT_90_90       = geometryToGeoJSON(POINT_90_90_GEOMETRY);
    private static final ObjectValue POINT_150_150     = geometryToGeoJSON(POINT_150_150_GEOMETRY);
    private static final ObjectValue POINT_89_99_89_99 = geometryToGeoJSON(POINT_89_99_89_99_GEOMETRY);

    private static final ObjectValue MULTIPOINT_1_2_150_150   = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] {
                                                                                    POINT_1_2_GEOMETRY,
                                                                                    POINT_150_150_GEOMETRY }));

    private static final ObjectValue MULTIPOINT_90_90_150_150 = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] {
                                                                                    POINT_90_90_GEOMETRY,
                                                                                    POINT_150_150_GEOMETRY }));

    private static final ObjectValue MULTIPOINT_1_2_90_90     = geometryToGeoJSON(GEO_FACTORY.createMultiPoint(new Point[] {
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


    private static final ObjectValue POLYGON_1 = geometryToGeoJSON(POLYGON_1_GEOMETRY);
    private static final ObjectValue POLYGON_2 = geometryToGeoJSON(POLYGON_2_GEOMETRY);
    private static final ObjectValue POLYGON_3 = geometryToGeoJSON(POLYGON_3_GEOMETRY);
    private static final ObjectValue POLYGON_4 = geometryToGeoJSON(POLYGON_4_GEOMETRY);
    private static final ObjectValue POLYGON_5 = geometryToGeoJSON(POLYGON_5_GEOMETRY);
    private static final ObjectValue POLYGON_6 = geometryToGeoJSON(POLYGON_6_GEOMETRY);

    private static final ObjectValue SELF_INTERSECTING_POLYGON = geometryToGeoJSON(GEO_FACTORY.createPolygon(
                                                                           GEO_FACTORY.createLinearRing(new Coordinate[] {
                                                                                    new Coordinate(0, 0),
                                                                                    new Coordinate(4, 4),
                                                                                    new Coordinate(4, 0),
                                                                                    new Coordinate(0, 4),
                                                                                    new Coordinate(0, 0) }), null));

    private static final ObjectValue LINE_1 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] {
                                                                                    new Coordinate(80, 100),
                                                                                    new Coordinate(200, 250) }));

    private static final ObjectValue LINE_2 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] {
                                                                                    new Coordinate(200, 100),
                                                                                    new Coordinate(200, 200),
                                                                                    new Coordinate(300, 200),
                                                                                    new Coordinate(300, 100) }));

    private static final ObjectValue LINE_3 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] {
                                                                                    new Coordinate(100, 100),
                                                                                    new Coordinate(200, 200) }));

    private static final ObjectValue LINE_4 = geometryToGeoJSON(GEO_FACTORY.createLineString(new Coordinate[] {
                                                                                    new Coordinate(0, 50),
                                                                                    new Coordinate(0, 150) }));

    private static final ObjectValue COLLECTION_1 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] {
                                                                                    POLYGON_1_GEOMETRY,
                                                                                    POLYGON_3_GEOMETRY }));

    private static final ObjectValue COLLECTION_2 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] {
                                                                                    POLYGON_1_GEOMETRY }));

    private static final ObjectValue COLLECTION_3 = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(new Geometry[] {
                                                                                    POINT_1_2_GEOMETRY,
                                                                                    POINT_90_90_GEOMETRY }));
    // @formatter:on

    @Test
    void libraryLoadsWithoutErrors() {
        var functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(GeographicFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void equalsTest() {
        assertThat(equalsExact(POINT_10_12, POINT_10_12)).isEqualTo(Value.TRUE);
        assertThat(equalsExact(POLYGON_4, POLYGON_4)).isEqualTo(Value.TRUE);
        assertThat(equalsExact(POINT_10_12, POINT_1_2)).isEqualTo(Value.FALSE);
        assertThat(equalsExact(POLYGON_4, POLYGON_1)).isEqualTo(Value.FALSE);
    }

    @Test
    void disjointTest() {
        assertThat(disjoint(POINT_1_2, POINT_10_12)).isEqualTo(Value.TRUE);
        assertThat(disjoint(POLYGON_4, POLYGON_1)).isEqualTo(Value.TRUE);
        assertThat(disjoint(POINT_10_12, POINT_10_12)).isEqualTo(Value.FALSE);
        assertThat(disjoint(POLYGON_4, POLYGON_4)).isEqualTo(Value.FALSE);
    }

    @Test
    void touchesTest() {
        assertThat(touches(POINT_10_12, POLYGON_4)).isEqualTo(Value.TRUE);
        assertThat(touches(POINT_10_12, POLYGON_1)).isEqualTo(Value.FALSE);
    }

    @Test
    void crossesTest() {
        assertThat(crosses(LINE_1, POLYGON_1)).isEqualTo(Value.TRUE);
        assertThat(crosses(POLYGON_4, POLYGON_1)).isEqualTo(Value.FALSE);
    }

    @Test
    void withinTest() {
        assertThat(within(LINE_3, POLYGON_1)).isEqualTo(Value.TRUE);
        assertThat(within(LINE_3, COLLECTION_1)).isEqualTo(Value.TRUE);
        assertThat(within(POINT_90_90, POLYGON_1)).isEqualTo(Value.FALSE);
        assertThat(within(POINT_90_90, COLLECTION_1)).isEqualTo(Value.FALSE);
    }

    @Test
    void containsTest() {
        assertThat(contains(POLYGON_1, LINE_3)).isEqualTo(Value.TRUE);
        assertThat(contains(COLLECTION_1, LINE_3)).isEqualTo(Value.TRUE);
        assertThat(contains(POLYGON_4, POINT_90_90)).isEqualTo(Value.FALSE);
        assertThat(contains(COLLECTION_1, POINT_90_90)).isEqualTo(Value.FALSE);
    }

    @Test
    void overlapsTest() {
        assertThat(overlaps(POLYGON_6, POLYGON_5)).isEqualTo(Value.TRUE);
        assertThat(overlaps(POLYGON_4, POLYGON_1)).isEqualTo(Value.FALSE);
    }

    @Test
    void intersectsTest() {
        assertThat(intersects(POLYGON_1, POLYGON_5)).isEqualTo(Value.TRUE);
        assertThat(intersects(POLYGON_4, POLYGON_5)).isEqualTo(Value.FALSE);
    }

    @Test
    void bufferTest() {
        val expectedGeometry = geometryToGeoJSON(POINT_1_2_GEOMETRY.buffer(10.0D));
        val actualGeometry   = buffer(POINT_1_2, Value.of(10.0D));
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void boundaryTest() {
        val expectedGeometry = POLYGON_2;
        val actualGeometry   = boundary(POLYGON_5);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void centroidTest() {
        val expectedGeometry = POINT_150_150;
        val actualGeometry   = centroid(POLYGON_1);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void convexHullTest() {
        val expectedGeometry = POLYGON_3;
        val actualGeometry   = convexHull(LINE_2);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionTest() {
        val expectedGeometry = MULTIPOINT_1_2_150_150;
        val actualGeometry   = union(POINT_1_2, POINT_150_150);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionSingleTest() {
        val expectedGeometry = POINT_10_12;
        val actualGeometry   = union(POINT_10_12);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionEmptyTest() {
        val expectedGeometry = geometryToGeoJSON(WGS84_FACTORY.createEmpty(-1));
        val actualGeometry   = union();
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void intersectionTest() {
        val expectedGeometry = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(200, 200)));
        val actualGeometry   = intersection(LINE_2, LINE_3);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void differenceTest() {
        val expectedGeometry = LINE_1;
        val actualGeometry   = difference(LINE_1, LINE_3);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void symDifferenceTest() {
        val expectedGeometry = MULTIPOINT_1_2_90_90;
        val actualGeometry   = symDifference(MULTIPOINT_1_2_150_150, MULTIPOINT_90_90_150_150);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void distanceTest() {
        val actualGeometry = distance(LINE_3, LINE_4);
        assertThat(actualGeometry).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actualGeometry).value().doubleValue()).isCloseTo(100.0D, within(0.001));
    }

    @Test
    void isWithinDistanceTest() {
        assertThat(isWithinDistance(LINE_3, LINE_4, Value.of(110))).isEqualTo(Value.TRUE);
        assertThat(isWithinDistance(LINE_3, LINE_4, Value.of(10))).isEqualTo(Value.FALSE);
    }

    @Test
    void lengthTest() {
        var result = length(LINE_4);
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(100.0D, within(0.001));
    }

    @Test
    void areaTest() {
        var result = area(POLYGON_1);
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(10000.0D, within(0.001));
    }

    @Test
    void isSimpleTest() {
        assertThat(isSimple(SELF_INTERSECTING_POLYGON)).isEqualTo(Value.FALSE);
        assertThat(isSimple(POLYGON_1)).isEqualTo(Value.TRUE);
    }

    @Test
    void isValidTest() {
        assertThat(isValid(SELF_INTERSECTING_POLYGON)).isEqualTo(Value.FALSE);
        assertThat(isValid(POLYGON_1)).isEqualTo(Value.TRUE);
    }

    @Test
    void isClosedTest() {
        val multiLineString    = """
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
        val multiLineStringVal = (ObjectValue) json(multiLineString);
        assertThat(isClosed(multiLineStringVal)).isEqualTo(Value.TRUE);
        assertThat(isClosed(POINT_10_12)).isEqualTo(Value.TRUE);
        assertThat(isClosed(MULTIPOINT_1_2_150_150)).isEqualTo(Value.TRUE);
        assertThat(isClosed(LINE_1)).isEqualTo(Value.FALSE);
        assertThat(isClosed(POLYGON_1)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void bagSizeTest() {
        var bagSizeLine       = bagSize(LINE_1);
        var bagSizeCollection = bagSize(COLLECTION_1);
        assertThat(bagSizeLine).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) bagSizeLine).value().intValue()).isEqualTo(1);
        assertThat(bagSizeCollection).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) bagSizeCollection).value().intValue()).isEqualTo(2);
    }

    @Test
    void oneAndOnlyTest() {
        val expected = POLYGON_1;
        val actual   = oneAndOnly(COLLECTION_2);
        assertThat(actual).isEqualTo(expected);
        assertThat(oneAndOnly(COLLECTION_1)).isInstanceOf(ErrorValue.class);
        assertThat(oneAndOnly(POLYGON_4)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void geometryIsInTest() {
        assertThat(geometryIsIn(POLYGON_1, COLLECTION_1)).isEqualTo(Value.TRUE);
        assertThat(geometryIsIn(POLYGON_6, COLLECTION_1)).isEqualTo(Value.FALSE);
        assertThat(geometryIsIn(POLYGON_4, POLYGON_1)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void geometryBagTest() {
        val expectedGeometry = COLLECTION_1;
        val actualGeometry   = geometryBag(POLYGON_1, POLYGON_3);
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void atLeastOneMemberOfTest() {
        assertThat(atLeastOneMemberOf(COLLECTION_1, COLLECTION_2)).isEqualTo(Value.TRUE);
        assertThat(atLeastOneMemberOf(COLLECTION_1, COLLECTION_3)).isEqualTo(Value.FALSE);
    }

    @Test
    void subsetTest() {
        assertThat(subset(COLLECTION_2, COLLECTION_1)).isEqualTo(Value.TRUE);
        assertThat(subset(COLLECTION_1, COLLECTION_3)).isEqualTo(Value.FALSE);
    }

    @Test
    void subsetTest_returnsFalseWhenThisIsLarger() {
        val geometryCollectionThis = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(
                new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY, POINT_1_2_GEOMETRY }));
        val geometryCollectionThat = geometryToGeoJSON(
                GEO_FACTORY.createGeometryCollection(new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY }));
        assertThat(subset(geometryCollectionThis, geometryCollectionThat)).isEqualTo(Value.FALSE);

    }

    @Test
    void geoDistanceCrsTest() {
        val crs            = Value.of(WGS84);
        val st             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        val de             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        val actualDistance = geoDistance(st, de, (TextValue) crs);
        assertThat(actualDistance).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actualDistance).value().doubleValue()).isGreaterThan(0.1);
    }

    @Test
    void geoDistanceTest() {
        val st             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        val de             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        val actualDistance = geodesicDistance(st, de);
        assertThat(actualDistance).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actualDistance).value().doubleValue()).isGreaterThan(0.1);
    }

    @Test
    void isWithingeoDistanceTest() {
        assertThat(isWithinGeodesicDistance(POINT_90_90, POINT_89_99_89_99, Value.of(1200))).isEqualTo(Value.TRUE);
        assertThat(isWithinGeodesicDistance(POINT_90_90, POINT_89_99_89_99, Value.of(2))).isEqualTo(Value.FALSE);
    }

    @Test
    void testMilesToMeterJsonNode() {
        val miles    = 1.0;
        val milesVal = Value.of(miles);
        val expected = miles * DistanceUtils.MILES_TO_KM * 1000;
        val actual   = milesToMeter((NumberValue) milesVal);
        assertThat(actual).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actual).value().doubleValue()).isCloseTo(expected, within(0.001));
    }

    @Test
    void testYardToMeter() {
        val yards    = 1.0;
        val yardVal  = Value.of(yards);
        val actual   = yardToMeter((NumberValue) yardVal);
        val expected = (yards / 1760) * DistanceUtils.MILES_TO_KM * 1000;
        assertThat(actual).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actual).value().doubleValue()).isCloseTo(expected, within(0.001));
    }

    @Test
    void testDegreeToMeter() {
        val inputVal = Value.of(1.0);
        val actual   = degreeToMeter((NumberValue) inputVal);
        val expected = 111195.07973436874;
        assertThat(actual).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) actual).value().doubleValue()).isCloseTo(expected, within(0.0001));
    }

    @Test
    void flattenGeometryBagTest_nestedArray() {
        val mapper    = new ObjectMapper();
        val arrayNode = mapper.createArrayNode();

        arrayNode.add(ValueJsonMarshaller.toJsonNode(POLYGON_1));
        arrayNode.add(ValueJsonMarshaller.toJsonNode(POLYGON_3));
        arrayNode.add(ValueJsonMarshaller.toJsonNode(POINT_1_2));

        val expectedGeometry = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(
                new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY, POINT_1_2_GEOMETRY }));
        val actualGeometry   = flattenGeometryBag((ArrayValue) ValueJsonMarshaller.fromJsonNode(arrayNode));
        assertThat(actualGeometry).isEqualTo(expectedGeometry);
    }

    @SneakyThrows
    private static ObjectValue geometryToGeoJSON(Geometry geo) {
        return (ObjectValue) json(GEOJSON_WRITER.write(geo));
    }

    @Test
    void kmlToGeoJSONTest() throws FactoryException {
        val kml              = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <Point>
                      <coordinates>-122.0822035425683,37.42228990140251,0</coordinates>
                    </Point>
                  </Placemark>
                </kml>
                """;
        val expectedGeometry = GEO_FACTORY.createPoint(new Coordinate(-122.0822035425683, 37.42228990140251, 0.0));
        expectedGeometry.setUserData(CRS.decode(WGS84));
        val expectedVal = geometryToGeoJSON(expectedGeometry);
        val result      = kmlToGeoJSON(Value.of(kml));
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONMultiGeometryTest() throws FactoryException {
        val kml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <name>MultiGeometry Example</name>
                    <MultiGeometry>
                      <Point>
                        <coordinates>1.0,2.0,0</coordinates>
                      </Point>
                      <LineString>
                        <coordinates>
                          0.0,0.0,0
                          1.0,1.0,0
                          2.0,0.0,0
                        </coordinates>
                      </LineString>
                      <Polygon>
                        <outerBoundaryIs>
                          <LinearRing>
                            <coordinates>
                              3.0,3.0,0
                              5.0,3.0,0
                              5.0,5.0,0
                              3.0,5.0,0
                              3.0,3.0,0
                            </coordinates>
                          </LinearRing>
                        </outerBoundaryIs>
                      </Polygon>
                    </MultiGeometry>
                  </Placemark>
                </kml>
                """;

        // Build the expected JTS GeometryCollection:
        var point      = GEO_FACTORY.createPoint(new Coordinate(1.0, 2.0, 0.0));
        var lineString = GEO_FACTORY.createLineString(new Coordinate[] { new Coordinate(0.0, 0.0, 0.0),
                new Coordinate(1.0, 1.0, 0.0), new Coordinate(2.0, 0.0, 0.0) });
        var polygon    = GEO_FACTORY
                .createPolygon(new Coordinate[] { new Coordinate(3.0, 3.0, 0.0), new Coordinate(5.0, 3.0, 0.0),
                        new Coordinate(5.0, 5.0, 0.0), new Coordinate(3.0, 5.0, 0.0), new Coordinate(3.0, 3.0, 0.0) });

        var multiGeometry = GEO_FACTORY.createGeometryCollection(new Geometry[] { point, lineString, polygon });

        // Set WGS84 explicitly
        multiGeometry.setUserData(CRS.decode(WGS84));

        // Convert the expected geometry to GeoJSON
        val expectedVal = geometryToGeoJSON(multiGeometry);

        // Run the code under test
        val result = kmlToGeoJSON(Value.of(kml));

        // Assert
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONMultiplePlacemarksTest() {
        val kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <name>First Placemark</name>
                      <Point>
                        <coordinates>-10,10,0</coordinates>
                      </Point>
                    </Placemark>
                    <Placemark>
                      <name>Second Placemark</name>
                      <LineString>
                        <coordinates>
                          -10,12,0
                          -9,13,0
                          -8,12,0
                        </coordinates>
                      </LineString>
                    </Placemark>
                  </Document>
                </kml>
                """;

        var point      = WGS84_FACTORY.createPoint(new Coordinate(-10.0, 10.0, 0.0));
        var lineString = WGS84_FACTORY.createLineString(new Coordinate[] { new Coordinate(-10.0, 12.0, 0.0),
                new Coordinate(-9.0, 13.0, 0.0), new Coordinate(-8.0, 12.0, 0.0) });

        var multiGeometry = WGS84_FACTORY.createGeometryCollection(new Geometry[] { point, lineString });

        val expectedVal = geometryToGeoJSON(multiGeometry);

        val result = kmlToGeoJSON(Value.of(kml));
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONPolygonWithHoleTest() throws FactoryException {
        val kml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <Polygon>
                      <outerBoundaryIs>
                        <LinearRing>
                          <coordinates>
                            10,10,0
                            20,10,0
                            20,20,0
                            10,20,0
                            10,10,0
                          </coordinates>
                        </LinearRing>
                      </outerBoundaryIs>
                      <innerBoundaryIs>
                        <LinearRing>
                          <coordinates>
                            12,12,0
                            18,12,0
                            18,18,0
                            12,18,0
                            12,12,0
                          </coordinates>
                        </LinearRing>
                      </innerBoundaryIs>
                    </Polygon>
                  </Placemark>
                </kml>
                """;

        // Outer ring
        var outer = GEO_FACTORY
                .createLinearRing(new Coordinate[] { new Coordinate(10, 10, 0), new Coordinate(20, 10, 0),
                        new Coordinate(20, 20, 0), new Coordinate(10, 20, 0), new Coordinate(10, 10, 0), });

        // Inner ring (hole)
        var inner = GEO_FACTORY
                .createLinearRing(new Coordinate[] { new Coordinate(12, 12, 0), new Coordinate(18, 12, 0),
                        new Coordinate(18, 18, 0), new Coordinate(12, 18, 0), new Coordinate(12, 12, 0), });

        // Polygon with hole
        var geoPolygon = GEO_FACTORY.createPolygon(outer, new org.locationtech.jts.geom.LinearRing[] { inner });
        geoPolygon.setUserData(CRS.decode(WGS84));

        val expectedVal = geometryToGeoJSON(geoPolygon);

        val result = kmlToGeoJSON(Value.of(kml));
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONParseErrorTest() {
        val invalidKml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <Point>
                      <!-- Missing <coordinates> or other syntax errors -->
                  <!-- Tag not closed properly -->
                """;

        val result = kmlToGeoJSON(Value.of(invalidKml));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void kmlToGeoJSONNoGeometriesTest() {
        val emptyKml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <name>No geometry here</name>
                  </Placemark>
                </kml>
                """;

        val result = kmlToGeoJSON(Value.of(emptyKml));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(GeographicFunctionLibrary.NO_GEOMETRIES_IN_KML_ERROR);
    }

    @Test
    void kmlToGeoJSONFeatureCollectionCaseTest() {
        val multiPlacemarksKml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <name>Placemark One</name>
                      <Point>
                        <coordinates>1,2,0</coordinates>
                      </Point>
                    </Placemark>
                    <Placemark>
                      <name>Placemark Two</name>
                      <LineString>
                        <coordinates>
                          1,2,0
                          2,3,0
                        </coordinates>
                      </LineString>
                    </Placemark>
                  </Document>
                </kml>
                """;

        val result = kmlToGeoJSON(Value.of(multiPlacemarksKml));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void wktToGeoJSONPointTest() {
        val wkt           = "POINT (10 20)";
        val result        = wktToGeoJSON(Value.of(wkt));
        val expectedPoint = GEO_FACTORY.createPoint(new Coordinate(10, 20));
        val expectedVal   = geometryToGeoJSON(expectedPoint);
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void wktToGeoJSONPolygonWithHoleTest() {
        val wkt         = "POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10),"
                + "         (12 12, 18 12, 18 18, 12 18, 12 12))";
        val result      = wktToGeoJSON(Value.of(wkt));
        val outer       = GEO_FACTORY.createLinearRing(new Coordinate[] { new Coordinate(10, 10),
                new Coordinate(20, 10), new Coordinate(20, 20), new Coordinate(10, 20), new Coordinate(10, 10) });
        val inner       = GEO_FACTORY.createLinearRing(new Coordinate[] { new Coordinate(12, 12),
                new Coordinate(18, 12), new Coordinate(18, 18), new Coordinate(12, 18), new Coordinate(12, 12) });
        val geoPolygon  = GEO_FACTORY.createPolygon(outer, new org.locationtech.jts.geom.LinearRing[] { inner });
        val expectedVal = geometryToGeoJSON(geoPolygon);
        assertThat(result).isEqualTo(expectedVal);
    }

    @Test
    void wktToGeoJSONParseErrorTest() {
        val invalidWkt = "POINT 10 20"; // missing parentheses => parse error
        val result     = wktToGeoJSON(Value.of(invalidWkt));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(GeographicFunctionLibrary.INVALID_WKT_ERROR);
    }

    @Test
    void gmlToGeoJSONPointTest() {
        val singlePointGml = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml" srsName="EPSG:4326">
                  <gml:coordinates>10,20</gml:coordinates>
                </gml:Point>
                """;
        val result         = gml3ToGeoJSON(Value.of(singlePointGml));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("Point");
        assertThat(geometry.getCoordinate().x).isEqualTo(10.0);
        assertThat(geometry.getCoordinate().y).isEqualTo(20.0);
    }

    @Test
    void gmlToGeoJSONFeatureCollectionTest() {
        val multiGeomGml = """
                <gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml" xmlns:myNS="http://example.com/myNS">
                  <gml:featureMember>
                    <myNS:someFeature>
                      <myNS:the_geom>
                        <gml:Point srsName="EPSG:4326">
                          <gml:coordinates>1,2</gml:coordinates>
                        </gml:Point>
                      </myNS:the_geom>
                    </myNS:someFeature>
                  </gml:featureMember>
                  <gml:featureMember>
                    <myNS:someFeature>
                      <myNS:the_geom>
                        <gml:LineString srsName="EPSG:4326">
                          <gml:coordinates>10,10 20,20</gml:coordinates>
                        </gml:LineString>
                      </myNS:the_geom>
                    </myNS:someFeature>
                  </gml:featureMember>
                </gml:FeatureCollection>
                """;
        val result       = gml3ToGeoJSON(Value.of(multiGeomGml));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("GeometryCollection");
        var gc = (GeometryCollection) geometry;
        assertThat(gc.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void gmlToGeoJSONNoGeometryTest() {
        val noGeomGml = """
                <gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml">
                  <gml:featureMember>
                    <SomeFeatureWithoutGeometry>
                      <SomeProperty>42</SomeProperty>
                    </SomeFeatureWithoutGeometry>
                  </gml:featureMember>
                </gml:FeatureCollection>
                """;
        val result    = gml3ToGeoJSON(Value.of(noGeomGml));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("No geometries in GML.");
    }

    @Test
    void gmlToGeoJSONInvalidTest() {
        val invalidGml = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                """;
        val result     = gml3ToGeoJSON(Value.of(invalidGml));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo(GeographicFunctionLibrary.FAILED_TO_PARSE_GML_ERROR);
    }

    @Test
    void gml2ToGeoJSONPointTest() {
        val gml2Point = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                  <gml:coordinates>10,20</gml:coordinates>
                </gml:Point>
                """;
        val result    = gml2ToGeoJSON(Value.of(gml2Point));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("Point");
        assertThat(geometry.getCoordinate()).isEqualTo(new Coordinate(10.0, 20.0));
    }

    @Test
    void gml2ToGeoJSONLineStringTest() {
        val gml2LineString = """
                <gml:LineString xmlns:gml="http://www.opengis.net/gml">
                  <gml:coordinates>10,10 20,20 30,10</gml:coordinates>
                </gml:LineString>
                """;
        val result         = gml2ToGeoJSON(Value.of(gml2LineString));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("LineString");
        assertThat(geometry.getCoordinates()).containsExactly(new Coordinate(10, 10), new Coordinate(20, 20),
                new Coordinate(30, 10));
    }

    @Test
    void gml2ToGeoJSONPolygonTest() {
        val gml2Polygon = """
                <gml:Polygon xmlns:gml="http://www.opengis.net/gml">
                  <gml:outerBoundaryIs>
                    <gml:LinearRing>
                      <gml:coordinates>10,10 20,10 20,20 10,20 10,10</gml:coordinates>
                    </gml:LinearRing>
                  </gml:outerBoundaryIs>
                </gml:Polygon>
                """;
        val result      = gml2ToGeoJSON(Value.of(gml2Polygon));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("Polygon");
        assertThat(geometry.getCoordinates()).containsExactly(new Coordinate(10, 10), new Coordinate(20, 10),
                new Coordinate(20, 20), new Coordinate(10, 20), new Coordinate(10, 10));
    }

    @Test
    void gml2ToGeoJSONMultiGeometryTest() {
        val gml2MultiGeom = """
                <gml:MultiGeometry xmlns:gml="http://www.opengis.net/gml">
                  <gml:geometryMember>
                    <gml:Point>
                      <gml:coordinates>10,20</gml:coordinates>
                    </gml:Point>
                  </gml:geometryMember>
                  <gml:geometryMember>
                    <gml:LineString>
                      <gml:coordinates>10,10 20,20</gml:coordinates>
                    </gml:LineString>
                  </gml:geometryMember>
                </gml:MultiGeometry>
                """;
        val result        = gml2ToGeoJSON(Value.of(gml2MultiGeom));
        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val geometry = GeographicFunctionLibrary.geoJsonToGeometry((ObjectValue) result);
        assertThat(geometry.getGeometryType()).isEqualTo("GeometryCollection");
        val gc = (GeometryCollection) geometry;
        assertThat(gc.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void gml2ToGeoJSONEmptyTest() {
        val gml2Empty = """
                <gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml">
                </gml:FeatureCollection>
                """;
        val result    = gml2ToGeoJSON(Value.of(gml2Empty));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo(GeographicFunctionLibrary.NO_GEOMETRIES_IN_GML_ERROR);
    }

    @Test
    void gml2ToGeoJSONInvalidTest() {
        val gml2Invalid = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                """;
        val result      = gml2ToGeoJSON(Value.of(gml2Invalid));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(GeographicFunctionLibrary.FAILED_TO_PARSE_GML_ERROR);
    }

}
