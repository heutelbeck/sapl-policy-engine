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

import lombok.val;
import lombok.experimental.UtilityClass;
import org.geotools.referencing.datum.DefaultEllipsoid;
import java.util.Locale;
import static tech.units.indriya.unit.Units.METRE;
import static io.sapl.functions.geo.GeographicFunctionLibrary.ERROR_UNSUPPORTED_CRS;
import io.sapl.functions.geo.GeographicFunctionLibrary.GeometryProblem;

/** Geographic coordinate systems with longitude and latitude in degrees. */
@UtilityClass
class GeographicCoordinateSystems {
    static DefaultEllipsoid ellipsoidOf(String code) {
        if ("CRS:84".equalsIgnoreCase(code)) {
            return ellipsoidOf("EPSG:4326");
        }
        val parts = code.toUpperCase(Locale.ROOT).split(":", -1);
        if (!code.chars().allMatch(character -> character < 128) || parts.length != 2 || !"EPSG".equals(parts[0])
                || !parts[1].matches("[0-9]+")) {
            throw new GeometryProblem(ERROR_UNSUPPORTED_CRS.formatted(code));
        }
        final int number;
        try {
            number = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new GeometryProblem(ERROR_UNSUPPORTED_CRS.formatted(code));
        }
        return switch (number) {
        case 4019, 4140, 4152, 4167, 4171, 4258, 4269, 4283, 4612, 4674, 6318, 6668, 7844 ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378137.0, 298.257222101, METRE);
        case 4024                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378245.0, 298.3, METRE);
        case 4030, 4148, 4326                                                             ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378137.0, 298.257223563, METRE);
        case 4047                                                                         ->
            DefaultEllipsoid.createEllipsoid(code, 6371007.0, 6371007.0, METRE);
        case 4230                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378388.0, 297.0, METRE);
        case 4267                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378206.4, 294.9786982138982, METRE);
        case 4277                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6377563.396, 299.3249646, METRE);
        case 4301, 4314                                                                   ->
            DefaultEllipsoid.createFlattenedSphere(code, 6377397.155, 299.1528128, METRE);
        case 4322                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378135.0, 298.26, METRE);
        case 4490                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378137.0, 298.257222101, METRE);
        case 4618                                                                         ->
            DefaultEllipsoid.createFlattenedSphere(code, 6378160.0, 298.25, METRE);
        default                                                                           ->
            throw new GeometryProblem(ERROR_UNSUPPORTED_CRS.formatted(code));
        };
    }
}
