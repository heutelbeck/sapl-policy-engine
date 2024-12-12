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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class WktConverter {
    private static final WKTReader WKT_READER = new WKTReader();

    /**
     * @param wkt a {@link Val} containing the WKT-String
     * @return a {@link Geometry}
     */
    public static Geometry wktToGeometry(Val wkt) throws ParseException {
        return wktToGeometry(wkt.getText());
    }

    /**
     * @param wkt a {@link Val} containing the WKT-String
     * @param factory a {@link GeometryFactory}
     * @return a {@link Geometry}
     */
    public static Geometry wktToGeometry(Val wkt, GeometryFactory factory) throws ParseException {
        return wktToGeometry(wkt.getText(), factory);
    }

    /**
     * @param wkt a WKT-String
     * @return a {@link Geometry}
     */
    public static Geometry wktToGeometry(String wkt) throws ParseException {
        return WKT_READER.read(wkt);
    }

    /**
     * @param wkt a WKT-String
     * @param factory a {@link GeometryFactory}
     * @return a {@link Geometry}
     */
    public static Geometry wktToGeometry(String wkt, GeometryFactory factory) throws ParseException {
        return WKT_READER.read(wkt);
    }
}
