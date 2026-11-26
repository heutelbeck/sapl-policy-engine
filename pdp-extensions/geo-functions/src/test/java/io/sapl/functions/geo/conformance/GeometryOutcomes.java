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
package io.sapl.functions.geo.conformance;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.conformance.FunctionOutcomes;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.Dimension;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.sapl.conformance.ConformanceCorpus.withPlaceholdersReplaced;
import static java.lang.Math.max;

/**
 * The outcomes of the cases of the geographic library in the function corpus.
 * A case that returns a geometry states it as {@code geometry: } with the
 * geometry in GeoJSON, and it agrees with a returned geometry that is
 * topologically equal: the same set of points, whatever its vertices and
 * their order. A case stated as {@code geometry within <tolerance>: } agrees
 * with a returned geometry of the same nonempty point-set dimension whose vertices each lie
 * within the tolerance of the stated geometry, and the other way round. Empty collection
 * members contribute no dimension. Every
 * other outcome is that of {@link FunctionOutcomes}.
 */
@UtilityClass
class GeometryOutcomes {

    private static final String GEOMETRY        = "geometry: ";
    private static final String GEOMETRY_WITHIN = "geometry within ";
    private static final String WITHIN_END      = ": ";

    /**
     * GeoJSON knows neither linear rings nor the surfaces and curves of GML,
     * which the library returns under these names. As point sets they are line
     * strings and polygons.
     */
    private static final Pattern LINEAR_RING   = Pattern.compile("\"type\"\\s*:\\s*\"LinearRing\"");
    private static final Pattern MULTI_SURFACE = Pattern.compile("\"type\"\\s*:\\s*\"MultiSurface\"");
    private static final Pattern MULTI_CURVE   = Pattern.compile("\"type\"\\s*:\\s*\"MultiCurve\"");

    /**
     * @param functionCase a case of the function corpus
     * @return the case with the outcome of this engine, or with the stated
     * outcome if it means the same
     */
    static Case withOutcome(Case functionCase) {
        if (!statesGeometry(functionCase)) {
            return FunctionOutcomes.withOutcome(functionCase);
        }
        val outcome = outcomeOf(functionCase);
        return functionCase.withExpectation(agrees(functionCase, outcome) ? functionCase.expectation() : outcome);
    }

    /**
     * @param functionCase a case of the function corpus
     * @return whether the stated outcome of the case is what this engine returns
     */
    static boolean agrees(Case functionCase) {
        return statesGeometry(functionCase) ? agrees(functionCase, outcomeOf(functionCase))
                : FunctionOutcomes.agrees(functionCase);
    }

    /**
     * @param functionCase a case of the function corpus
     * @return what the function of the case returns, as an outcome line of the
     * corpus, in the form of a geometry if the case states one
     */
    static String outcomeOf(Case functionCase) {
        if (!statesGeometry(functionCase)) {
            return FunctionOutcomes.outcomeOf(functionCase);
        }
        val result = FunctionOutcomes.resultOf(functionCase);
        if (result instanceof ObjectValue) {
            return prefixOf(functionCase.expectation()) + ValueJsonMarshaller.toJsonString(result);
        }
        return FunctionOutcomes.written(result);
    }

    private static boolean statesGeometry(Case functionCase) {
        val expectation = functionCase.expectation();
        return expectation.startsWith(GEOMETRY)
                || expectation.startsWith(GEOMETRY_WITHIN) && expectation.contains(WITHIN_END);
    }

    private static String prefixOf(String outcome) {
        return outcome.substring(0, outcome.indexOf(WITHIN_END) + WITHIN_END.length());
    }

    private static boolean agrees(Case functionCase, String outcome) {
        val stated = withPlaceholdersReplaced(functionCase.expectation(), functionCase.origin());
        val prefix = prefixOf(stated);
        if (!outcome.startsWith(prefix)) {
            return false;
        }
        val expected = geometryOf(stated.substring(prefix.length()));
        val returned = geometryOf(outcome.substring(prefix.length()));
        if (expected.isEmpty() || returned.isEmpty()) {
            return false;
        }
        if (stated.startsWith(GEOMETRY)) {
            return areTopologicallyEqual(expected.get(), returned.get());
        }
        val tolerance = new BigDecimal(
                prefix.substring(GEOMETRY_WITHIN.length(), prefix.length() - WITHIN_END.length()));
        return areWithinTolerance(expected.get(), returned.get(), tolerance.doubleValue());
    }

    private static Optional<Geometry> geometryOf(String geoJson) {
        var json = LINEAR_RING.matcher(geoJson).replaceAll("\"type\":\"LineString\"");
        json = MULTI_SURFACE.matcher(json).replaceAll("\"type\":\"MultiPolygon\"");
        json = MULTI_CURVE.matcher(json).replaceAll("\"type\":\"MultiLineString\"");
        try {
            return Optional.of(new GeoJsonReader().read(json));
        } catch (ParseException notGeoJson) {
            return Optional.empty();
        }
    }

    /**
     * Collections are united first, so that their parts form one set of points,
     * which is what the comparison of the topology needs.
     */
    private static boolean areTopologicallyEqual(Geometry expected, Geometry returned) {
        if (expected.isEmpty() || returned.isEmpty()) {
            return expected.isEmpty() && returned.isEmpty();
        }
        return expected.union().equalsTopo(returned.union());
    }

    private static boolean areWithinTolerance(Geometry expected, Geometry returned, double tolerance) {
        if (expected.isEmpty() || returned.isEmpty()) {
            return expected.isEmpty() && returned.isEmpty();
        }
        return pointSetDimension(expected) == pointSetDimension(returned)
                && DiscreteHausdorffDistance.distance(expected, returned) <= tolerance;
    }

    private static int pointSetDimension(Geometry geometry) {
        if (geometry instanceof GeometryCollection collection) {
            var dimension = Dimension.FALSE;
            for (int member = 0; member < collection.getNumGeometries(); member++) {
                dimension = max(dimension, pointSetDimension(collection.getGeometryN(member)));
            }
            return dimension;
        }
        return geometry.isEmpty() ? Dimension.FALSE : geometry.getDimension();
    }

}
