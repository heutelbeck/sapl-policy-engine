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
package io.sapl.geofunctions;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WktConverter {

    public static Val wktToGML(Val wkt) {

        try {
            return GeometryConverter.geometryToGML(wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public static Val wktToKML(Val wkt) {

        try {
            return GeometryConverter.geometryToKML(wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public static Val wktToGeoJsonString(Val wkt) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public static Geometry wktToGeometry(Val wkt) throws ParseException {

        return (new WKTReader()).read(wkt.getText());
    }

}
