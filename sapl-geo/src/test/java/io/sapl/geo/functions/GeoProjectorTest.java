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
package io.sapl.geo.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@TestInstance(Lifecycle.PER_CLASS)
class GeoProjectorTest {

    static Stream<GeoProjector> projectorProvider() throws FactoryException {
        return Stream.of(
                new GeoProjector(CrsConst.WGS84_CRS.getValue(), true, CrsConst.WEB_MERCATOR_CRS.getValue(), false),
                new GeoProjector());
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @MethodSource("projectorProvider")
    void testProjectValidGeometry(GeoProjector geoProjector)
            throws MismatchedDimensionException, org.geotools.api.referencing.operation.TransformException {
        final var geometryFactory = new GeometryFactory();
        final var point           = geometryFactory.createPoint(new Coordinate(10.0, 20.0));

        final var projectedGeometry = geoProjector.project(point);

        assertEquals(1113194.9079327357, projectedGeometry.getCoordinate().x, 0.5);
        assertEquals(2273030.926987689, projectedGeometry.getCoordinate().y, 0.5);
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @MethodSource("projectorProvider")
    void testReProjectValidGeometry(GeoProjector geoProjector)
            throws MismatchedDimensionException, org.geotools.api.referencing.operation.TransformException {
        final var geometryFactory = new GeometryFactory();
        final var point           = geometryFactory.createPoint(new Coordinate(1113194.9079327357, 2273030.926987689));

        final var reProjectedGeometry = geoProjector.reProject(point);

        assertEquals(10.0, reProjectedGeometry.getCoordinate().x, 0.5);
        assertEquals(20.0, reProjectedGeometry.getCoordinate().y, 0.5);
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @MethodSource("projectorProvider")
    void testInvalidGeometry(GeoProjector geoProjector) {
        assertThrows(NullPointerException.class, () -> {
            geoProjector.project(null);
        });
    }
}
