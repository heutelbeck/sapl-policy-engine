package io.sapl.geo.functions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import javax.xml.crypto.dsig.TransformException;
import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

@TestInstance(Lifecycle.PER_CLASS)
class GeoProjectorTest {

    static Stream<GeoProjector> projectorProvider() throws FactoryException {
        return Stream.of(new GeoProjector(CrsConst.WGS84_CRS, true, CrsConst.WEB_MERCATOR_CRS, false),
                new GeoProjector());
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @MethodSource("projectorProvider")
    void testProjectValidGeometry(GeoProjector geoProjector) throws TransformException, MismatchedDimensionException,
            org.geotools.api.referencing.operation.TransformException {

        GeometryFactory geometryFactory = new GeometryFactory();
        Point           point           = geometryFactory.createPoint(new Coordinate(10.0, 20.0));

        Geometry projectedGeometry = geoProjector.project(point);

        assertEquals(1113194.9079327357, projectedGeometry.getCoordinate().x, 0.5);
        assertEquals(2273030.926987689, projectedGeometry.getCoordinate().y, 0.5);
    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @MethodSource("projectorProvider")
    void testReProjectValidGeometry(GeoProjector geoProjector) throws TransformException, MismatchedDimensionException,
            org.geotools.api.referencing.operation.TransformException {

        GeometryFactory geometryFactory = new GeometryFactory();
        Point           point           = geometryFactory
                .createPoint(new Coordinate(1113194.9079327357, 2273030.926987689));

        Geometry reProjectedGeometry = geoProjector.reProject(point);

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
