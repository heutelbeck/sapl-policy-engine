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

import static io.sapl.functions.geo.GeographicFunctionLibrary.MAX_GEOMETRY_COUNT;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geometryBag;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geometryToGeoJSON;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("GeographicFunctionLibrary geometry bag limits")
class GeographicFunctionLibraryGeometryBagLimitTests {

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory();
    private static final ObjectValue     POINT       = (ObjectValue) geometryToGeoJSON(
            GEO_FACTORY.createPoint(new Coordinate(1.0, 2.0)));

    @Test
    @DisplayName("geometryBag returns an error when the collection member limit is exceeded")
    void whenGeometryBagExceedsCollectionLimitThenErrorValue() {
        val result = geometryBag(pointValuesExceedingCollectionLimit());

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("members"));
    }

    @Test
    @DisplayName("flattenGeometryBag returns an error when the collection member limit is exceeded")
    void whenFlattenGeometryBagExceedsCollectionLimitThenErrorValue() {
        val result = GeographicFunctionLibrary.flattenGeometryBag(pointArrayExceedingCollectionLimit());

        assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("members"));
    }

    private static ObjectValue[] pointValuesExceedingCollectionLimit() {
        val points = new ObjectValue[MAX_GEOMETRY_COUNT + 1];
        Arrays.fill(points, POINT);
        return points;
    }

    private static ArrayValue pointArrayExceedingCollectionLimit() {
        val points = new Value[MAX_GEOMETRY_COUNT + 1];
        Arrays.fill(points, POINT);
        return new ArrayValue(points);
    }

}
