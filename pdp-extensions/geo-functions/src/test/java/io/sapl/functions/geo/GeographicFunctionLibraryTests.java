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

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static io.sapl.functions.geo.GeographicFunctionLibrary.NOT_A_GEOMETRY_COLLECTION_ERROR;
import static io.sapl.functions.geo.GeographicFunctionLibrary.area;
import static io.sapl.functions.geo.GeographicFunctionLibrary.atLeastOneMemberOf;
import static io.sapl.functions.geo.GeographicFunctionLibrary.bagSize;
import static io.sapl.functions.geo.GeographicFunctionLibrary.boundary;
import static io.sapl.functions.geo.GeographicFunctionLibrary.buffer;
import static io.sapl.functions.geo.GeographicFunctionLibrary.centroid;
import static io.sapl.functions.geo.GeographicFunctionLibrary.contains;
import static io.sapl.functions.geo.GeographicFunctionLibrary.convexHull;
import static io.sapl.functions.geo.GeographicFunctionLibrary.crosses;
import static io.sapl.functions.geo.GeographicFunctionLibrary.degreeToMeter;
import static io.sapl.functions.geo.GeographicFunctionLibrary.difference;
import static io.sapl.functions.geo.GeographicFunctionLibrary.disjoint;
import static io.sapl.functions.geo.GeographicFunctionLibrary.distance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.equalsExact;
import static io.sapl.functions.geo.GeographicFunctionLibrary.flattenGeometryBag;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geoDistance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geometryBag;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geometryIsIn;
import static io.sapl.functions.geo.GeographicFunctionLibrary.gml3ToGeoJSON;
import static io.sapl.functions.geo.GeographicFunctionLibrary.intersection;
import static io.sapl.functions.geo.GeographicFunctionLibrary.intersects;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isClosed;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isSimple;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isValid;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isWithinDistance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isWithinGeoDistance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.kmlToGeoJSON;
import static io.sapl.functions.geo.GeographicFunctionLibrary.length;
import static io.sapl.functions.geo.GeographicFunctionLibrary.milesToMeter;
import static io.sapl.functions.geo.GeographicFunctionLibrary.oneAndOnly;
import static io.sapl.functions.geo.GeographicFunctionLibrary.overlaps;
import static io.sapl.functions.geo.GeographicFunctionLibrary.subset;
import static io.sapl.functions.geo.GeographicFunctionLibrary.symDifference;
import static io.sapl.functions.geo.GeographicFunctionLibrary.touches;
import static io.sapl.functions.geo.GeographicFunctionLibrary.union;
import static io.sapl.functions.geo.GeographicFunctionLibrary.within;
import static io.sapl.functions.geo.GeographicFunctionLibrary.wktToGeoJSON;
import static io.sapl.functions.geo.GeographicFunctionLibrary.yardToMeter;
import static org.assertj.core.api.Assertions.assertThat;

import org.geotools.api.referencing.FactoryException;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.spatial4j.distance.DistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import lombok.SneakyThrows;

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
    private static final Val POLYGON_2 = geometryToGeoJSON(POLYGON_2_GEOMETRY);
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
        assertThatVal(equalsExact(POINT_10_12, POINT_10_12)).isTrue();
        assertThatVal(equalsExact(POLYGON_4, POLYGON_4)).isTrue();
        assertThatVal(equalsExact(POINT_10_12, POINT_1_2)).isFalse();
        assertThatVal(equalsExact(POLYGON_4, POLYGON_1)).isFalse();
    }

    @Test
    void disjointTest() {
        assertThatVal(disjoint(POINT_1_2, POINT_10_12)).isTrue();
        assertThatVal(disjoint(POLYGON_4, POLYGON_1)).isTrue();
        assertThatVal(disjoint(POINT_10_12, POINT_10_12)).isFalse();
        assertThatVal(disjoint(POLYGON_4, POLYGON_4)).isFalse();
    }

    @Test
    void touchesTest() {
        assertThatVal(touches(POINT_10_12, POLYGON_4)).isTrue();
        assertThatVal(touches(POINT_10_12, POLYGON_1)).isFalse();
    }

    @Test
    void crossesTest() {
        assertThatVal(crosses(LINE_1, POLYGON_1)).isTrue();
        assertThatVal(crosses(POLYGON_4, POLYGON_1)).isFalse();
    }

    @Test
    void withinTest() {
        assertThatVal(within(LINE_3, POLYGON_1)).isTrue();
        assertThatVal(within(LINE_3, COLLECTION_1)).isTrue();
        assertThatVal(within(POINT_90_90, POLYGON_1)).isFalse();
        assertThatVal(within(POINT_90_90, COLLECTION_1)).isFalse();
    }

    @Test
    void containsTest() {
        assertThatVal(contains(POLYGON_1, LINE_3)).isTrue();
        assertThatVal(contains(COLLECTION_1, LINE_3)).isTrue();
        assertThatVal(contains(POLYGON_4, POINT_90_90)).isFalse();
        assertThatVal(contains(COLLECTION_1, POINT_90_90)).isFalse();
    }

    @Test
    void overlapsTest() {
        assertThatVal(overlaps(POLYGON_6, POLYGON_5)).isTrue();
        assertThatVal(overlaps(POLYGON_4, POLYGON_1)).isFalse();
    }

    @Test
    void intersectsTest() {
        assertThatVal(intersects(POLYGON_1, POLYGON_5)).isTrue();
        assertThatVal(intersects(POLYGON_4, POLYGON_5)).isFalse();
    }

    @Test
    void bufferTest() {
        final var expectedGeometry = geometryToGeoJSON(POINT_1_2_GEOMETRY.buffer(10.0D));
        final var actualGeometry   = buffer(POINT_1_2, Val.of(10.0D));
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void boundaryTest() {
        final var expectedGeometry = POLYGON_2;
        final var actualGeometry   = boundary(POLYGON_5);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void centroidTest() {
        final var expectedGeometry = POINT_150_150;
        final var actualGeometry   = centroid(POLYGON_1);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void convexHullTest() {
        final var expectedGeometry = POLYGON_3;
        final var actualGeometry   = convexHull(LINE_2);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionTest() {
        final var expectedGeometry = MULTIPOINT_1_2_150_150;
        final var actualGeometry   = union(new Val[] { POINT_1_2, POINT_150_150 });
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionSingleTest() {
        final var expectedGeometry = POINT_10_12;
        final var actualGeometry   = union(new Val[] { POINT_10_12 });
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void unionEmptyTest() {
        final var expectedGeometry = geometryToGeoJSON(WGS84_FACTORY.createEmpty(-1));
        final var actualGeometry   = union(new Val[] {});
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void intersectionTest() {
        final var expectedGeometry = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(200, 200)));
        final var actualGeometry   = intersection(LINE_2, LINE_3);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void differenceTest() {
        final var expectedGeometry = LINE_1;
        final var actualGeometry   = difference(LINE_1, LINE_3);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void symDifferenceTest() {
        final var expectedGeometry = MULTIPOINT_1_2_90_90;
        final var actualGeometry   = symDifference(MULTIPOINT_1_2_150_150, MULTIPOINT_90_90_150_150);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void distanceTest() {
        final var actualGeometry = distance(LINE_3, LINE_4);
        assertThatVal(actualGeometry).hasValue().withTolerance(0).isEqualTo(100.0D);
    }

    @Test
    void isWithinDistanceTest() {
        assertThatVal(isWithinDistance(LINE_3, LINE_4, Val.of(110))).isTrue();
        assertThatVal(isWithinDistance(LINE_3, LINE_4, Val.of(10))).isFalse();
    }

    @Test
    void lenghtTest() {
        assertThatVal(length(LINE_4)).hasValue().withTolerance(0).isEqualTo(100.0D);
    }

    @Test
    void areaTest() {
        assertThatVal(area(POLYGON_1)).hasValue().withTolerance(0).isEqualTo(10000.0D);
    }

    @Test
    void isSimpleTest() {
        assertThatVal(isSimple(SELF_INTERSECTING_POLYGON)).isFalse();
        assertThatVal(isSimple(POLYGON_1)).isTrue();
    }

    @Test
    void isValidTest() {
        assertThatVal(isValid(SELF_INTERSECTING_POLYGON)).isFalse();
        assertThatVal(isValid(POLYGON_1)).isTrue();
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
        assertThatVal(isClosed(multiLineStringVal)).isTrue();
        assertThatVal(isClosed(POINT_10_12)).isTrue();
        assertThatVal(isClosed(MULTIPOINT_1_2_150_150)).isTrue();
        assertThatVal(isClosed(LINE_1)).isFalse();
        assertThatVal(isClosed(POLYGON_1)).isError();
    }

    @Test
    void bagSizeTest() {
        assertThat(bagSize(LINE_1).get().asInt()).isEqualTo(1);
        assertThat(bagSize(COLLECTION_1).get().asInt()).isEqualTo(2);
    }

    @Test
    void oneAndOnlyTest() {
        final var expected = POLYGON_1;
        final var actual   = oneAndOnly(COLLECTION_2);
        assertThatVal(actual).isEqualTo(expected);
        assertThatVal(oneAndOnly(COLLECTION_1))
                .isError(GeographicFunctionLibrary.INCORRECT_NUMER_OF_GEOEMTRIES_ERROR);
        assertThatVal(oneAndOnly(POLYGON_4))
                .isError(GeographicFunctionLibrary.INCORRECT_NUMER_OF_GEOEMTRIES_ERROR);
    }

    @Test
    void geometryIsInTest() {
        assertThatVal(geometryIsIn(POLYGON_1, COLLECTION_1)).isTrue();
        assertThatVal(geometryIsIn(POLYGON_6, COLLECTION_1)).isFalse();
        assertThatVal(geometryIsIn(POLYGON_4, POLYGON_1)).isError(NOT_A_GEOMETRY_COLLECTION_ERROR);
    }

    @Test
    void geometryBagTest() {
        final var expectedGeometry = COLLECTION_1;
        final var actualGeometry   = geometryBag(POLYGON_1, POLYGON_3);
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @Test
    void atLeastOneMemberOfTest() {
        assertThatVal(atLeastOneMemberOf(COLLECTION_1, COLLECTION_2)).isTrue();
        assertThatVal(atLeastOneMemberOf(COLLECTION_1, COLLECTION_3)).isFalse();
    }

    @Test
    void subsetTest() {
        assertThatVal(subset(COLLECTION_2, COLLECTION_1)).isTrue();
        assertThatVal(subset(COLLECTION_1, COLLECTION_3)).isFalse();
    }

    @Test
    void subsetTest_returnsFalseWhenThisIsLarger() {
        final var geometryCollectionThis = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(
                new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY, POINT_1_2_GEOMETRY }));
        final var geometryCollectionThat = geometryToGeoJSON(
                GEO_FACTORY.createGeometryCollection(new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY }));
        assertThatVal(subset(geometryCollectionThis, geometryCollectionThat)).isFalse();

    }

    @Test
    void geoDistanceCrsTest() {
        final var crs            = Val.of(WGS84);
        final var st             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        final var de             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        final var actualDistance = geoDistance(st, de, crs);
        assertThatVal(actualDistance).matches(v -> v.getDouble() > 0.1);
    }

    @Test
    void geoDistanceTest() {
        final var st             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.0)));
        final var de             = geometryToGeoJSON(GEO_FACTORY.createPoint(new Coordinate(10.0, 10.000001)));
        final var actualDistance = geoDistance(st, de);
        assertThatVal(actualDistance).matches(v -> v.getDouble() > 0.1);
    }

    @Test
    void isWithingeoDistanceTest() {
        assertThatVal(isWithinGeoDistance(POINT_90_90, POINT_89_99_89_99, Val.of(1200))).isTrue();
        assertThatVal(isWithinGeoDistance(POINT_90_90, POINT_89_99_89_99, Val.of(2))).isFalse();
    }

    @Test
    void testMilesToMeterJsonNode() {
        final var miles    = 1.0;
        final var milesVal = Val.of(miles);
        final var expected = miles * DistanceUtils.MILES_TO_KM * 1000;
        final var actual   = milesToMeter(milesVal);
        assertThatVal(actual).hasValue().withTolerance(0).isEqualTo(expected);
    }

    @Test
    void testYardToMeter() {
        final var yards    = 1.0;
        final var yardVal  = Val.of(yards);
        final var actual   = yardToMeter(yardVal);
        final var expected = (yards / 1760) * DistanceUtils.MILES_TO_KM * 1000;
        assertThatVal(actual).hasValue().withTolerance(0).isEqualTo(expected);
    }

    @Test
    void testDegreeToMeter() {
        final var inputVal = Val.of(1.0);
        final var actual   = degreeToMeter(inputVal);
        final var expected = 111195.07973436874;
        assertThatVal(actual).hasValue().withTolerance(0.0001).isEqualTo(expected);
    }

    @Test
    void flattenGometryBagTest_nestedArray() {
        final var mapper    = new ObjectMapper();
        final var arrayNode = mapper.createArrayNode();

        arrayNode.add(mapper.valueToTree(POLYGON_1.get()));
        arrayNode.add(mapper.valueToTree(POLYGON_3.get()));
        arrayNode.add(mapper.valueToTree(POINT_1_2.get()));

        final var expectedGeometry = geometryToGeoJSON(GEO_FACTORY.createGeometryCollection(
                new Geometry[] { POLYGON_1_GEOMETRY, POLYGON_3_GEOMETRY, POINT_1_2_GEOMETRY }));
        final var actualGeometry   = flattenGeometryBag(Val.of(arrayNode));
        assertThatVal(actualGeometry).isEqualTo(expectedGeometry);
    }

    @SneakyThrows
    private static Val geometryToGeoJSON(Geometry geo) {
        return Val.ofJson(GEOJSON_WRITER.write(geo));
    }

    @Test
    void kmlToGeoJSONTest() throws FactoryException {
        final var kml              = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <Point>
                      <coordinates>-122.0822035425683,37.42228990140251,0</coordinates>
                    </Point>
                  </Placemark>
                </kml>
                """;
        final var expectedGeometry = GEO_FACTORY
                .createPoint(new Coordinate(-122.0822035425683, 37.42228990140251, 0.0));
        expectedGeometry.setUserData(CRS.decode(WGS84));
        final var expectedVal = geometryToGeoJSON(expectedGeometry);
        final var result      = kmlToGeoJSON(Val.of(kml));
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONMultiGeometryTest() throws FactoryException {
        final var kml = """
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
        final var expectedVal = geometryToGeoJSON(multiGeometry);

        // Run the code under test
        final var result = kmlToGeoJSON(Val.of(kml));

        // Assert
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONMultiplePlacemarksTest() {
        final var kml = """
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

        final var expectedVal = geometryToGeoJSON(multiGeometry);

        final var result = kmlToGeoJSON(Val.of(kml));
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONPolygonWithHoleTest() throws FactoryException {
        final var kml = """
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
        var polygon = GEO_FACTORY.createPolygon(outer, new org.locationtech.jts.geom.LinearRing[] { inner });
        polygon.setUserData(CRS.decode(WGS84));

        final var expectedVal = geometryToGeoJSON(polygon);

        final var result = kmlToGeoJSON(Val.of(kml));
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void kmlToGeoJSONParseErrorTest() {
        final var invalidKml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <Point>
                      <!-- Missing <coordinates> or other syntax errors -->
                  <!-- Tag not closed properly -->
                """;

        final var result = kmlToGeoJSON(Val.of(invalidKml));
        assertThatVal(result).isError();
    }

    @Test
    void kmlToGeoJSONNoGeometriesTest() {
        final var emptyKml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <name>No geometry here</name>
                  </Placemark>
                </kml>
                """;

        final var result = kmlToGeoJSON(Val.of(emptyKml));
        assertThatVal(result).isError(GeographicFunctionLibrary.NO_GEOMETRIES_IN_KML_ERROR);
    }

    @Test
    void kmlToGeoJSONFeatureCollectionCaseTest() {
        final var multiPlacemarksKml = """
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

        final var result = kmlToGeoJSON(Val.of(multiPlacemarksKml));
        assertThatVal(result).hasValue();
    }

    @Test
    void wktToGeoJSONPointTest() {
        final var wkt           = "POINT (10 20)";
        final var result        = wktToGeoJSON(Val.of(wkt));
        final var expectedPoint = GEO_FACTORY.createPoint(new Coordinate(10, 20));
        final var expectedVal   = geometryToGeoJSON(expectedPoint);
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void wktToGeoJSONPolygonWithHoleTest() {
        final var wkt         = "POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10),"
                + "         (12 12, 18 12, 18 18, 12 18, 12 12))";
        final var result      = wktToGeoJSON(Val.of(wkt));
        final var outer       = GEO_FACTORY.createLinearRing(new Coordinate[] { new Coordinate(10, 10),
                new Coordinate(20, 10), new Coordinate(20, 20), new Coordinate(10, 20), new Coordinate(10, 10) });
        final var inner       = GEO_FACTORY.createLinearRing(new Coordinate[] { new Coordinate(12, 12),
                new Coordinate(18, 12), new Coordinate(18, 18), new Coordinate(12, 18), new Coordinate(12, 12) });
        final var polygon     = GEO_FACTORY.createPolygon(outer, new org.locationtech.jts.geom.LinearRing[] { inner });
        final var expectedVal = geometryToGeoJSON(polygon);
        assertThatVal(result).isEqualTo(expectedVal);
    }

    @Test
    void wktToGeoJSONParseErrorTest() {
        final var invalidWkt = "POINT 10 20"; // missing parentheses => parse error
        final var result     = wktToGeoJSON(Val.of(invalidWkt));
        assertThatVal(result).isError().contains(GeographicFunctionLibrary.INVALID_WKT_ERROR);
    }

    @Test
    void gmlToGeoJSONPointTest() {
        final var singlePointGml = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml" srsName="EPSG:4326">
                  <gml:coordinates>10,20</gml:coordinates>
                </gml:Point>
                """;
        final var result         = gml3ToGeoJSON(Val.of(singlePointGml));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("Point");
        assertThat(geometry.getCoordinate().x).isEqualTo(10.0);
        assertThat(geometry.getCoordinate().y).isEqualTo(20.0);
    }

    @Test
    void gmlToGeoJSONFeatureCollectionTest() {
        final var multiGeomGml = """
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
        final var result       = gml3ToGeoJSON(Val.of(multiGeomGml));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("GeometryCollection");
        var gc = (GeometryCollection) geometry;
        assertThat(gc.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void gmlToGeoJSONNoGeometryTest() {
        final var noGeomGml = """
                <gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml">
                  <gml:featureMember>
                    <SomeFeatureWithoutGeometry>
                      <SomeProperty>42</SomeProperty>
                    </SomeFeatureWithoutGeometry>
                  </gml:featureMember>
                </gml:FeatureCollection>
                """;
        final var result    = gml3ToGeoJSON(Val.of(noGeomGml));
        assertThatVal(result).isError().contains("No geometries in GML.");
    }

    @Test
    void gmlToGeoJSONInvalidTest() {
        final var invalidGml = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                """;
        final var result     = gml3ToGeoJSON(Val.of(invalidGml));
        assertThatVal(result).isError(GeographicFunctionLibrary.FAILED_TO_PARSE_GML_ERROR);
    }

    @Test
    void gml2ToGeoJSONPointTest() {
        final var gml2Point = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                  <gml:coordinates>10,20</gml:coordinates>
                </gml:Point>
                """;
        final var result    = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2Point));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("Point");
        assertThat(geometry.getCoordinate()).isEqualTo(new Coordinate(10.0, 20.0));
    }

    @Test
    void gml2ToGeoJSONLineStringTest() {
        final var gml2LineString = """
                <gml:LineString xmlns:gml="http://www.opengis.net/gml">
                  <gml:coordinates>10,10 20,20 30,10</gml:coordinates>
                </gml:LineString>
                """;
        final var result         = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2LineString));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("LineString");
        assertThat(geometry.getCoordinates()).containsExactly(new Coordinate(10, 10), new Coordinate(20, 20),
                new Coordinate(30, 10));
    }

    @Test
    void gml2ToGeoJSONPolygonTest() {
        final var gml2Polygon = """
                <gml:Polygon xmlns:gml="http://www.opengis.net/gml">
                  <gml:outerBoundaryIs>
                    <gml:LinearRing>
                      <gml:coordinates>10,10 20,10 20,20 10,20 10,10</gml:coordinates>
                    </gml:LinearRing>
                  </gml:outerBoundaryIs>
                </gml:Polygon>
                """;
        final var result      = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2Polygon));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("Polygon");
        assertThat(geometry.getCoordinates()).containsExactly(new Coordinate(10, 10), new Coordinate(20, 10),
                new Coordinate(20, 20), new Coordinate(10, 20), new Coordinate(10, 10));
    }

    @Test
    void gml2ToGeoJSONMultiGeometryTest() {
        final var gml2MultiGeom = """
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
        final var result        = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2MultiGeom));
        assertThatVal(result).hasValue();
        final var geometry = GeographicFunctionLibrary.geoJsonToGeometry(result);
        assertThat(geometry.getGeometryType()).isEqualTo("GeometryCollection");
        final var gc = (GeometryCollection) geometry;
        assertThat(gc.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void gml2ToGeoJSONEmptyTest() {
        final var gml2Empty = """
                <gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml">
                </gml:FeatureCollection>
                """;
        final var result    = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2Empty));
        assertThatVal(result).isError(GeographicFunctionLibrary.NO_GEOMETRIES_IN_GML_ERROR);
    }

    @Test
    void gml2ToGeoJSONInvalidTest() {
        final var gml2Invalid = """
                <gml:Point xmlns:gml="http://www.opengis.net/gml">
                """;
        final var result      = GeographicFunctionLibrary.gml2ToGeoJSON(Val.of(gml2Invalid));
        assertThatVal(result).isError().contains(GeographicFunctionLibrary.FAILED_TO_PARSE_GML_ERROR);
    }
}
