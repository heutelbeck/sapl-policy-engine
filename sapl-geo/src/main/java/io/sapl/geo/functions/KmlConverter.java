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
import org.locationtech.jts.io.kml.KMLReader;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class KmlConverter {
    private static final KMLReader KML_READER = new KMLReader();

    /**
     * @param kml a {@link Val} containing the KML-String
     * @return a {@link Geometry}
     */
    public static Geometry kmlToGeometry(Val kml) throws ParseException {
        return kmlToGeometry(kml.getText());
    }

    /**
     * @param kml a {@link Val} containing the KML-String
     * @param factory a {@link GeometryFactory}
     * @return a {@link Geometry}
     */
    public static Geometry kmlToGeometry(Val kml, GeometryFactory factory) throws ParseException {
        return kmlToGeometry(kml.getText(), factory);
    }

    /**
     * @param kml a KML-String
     * @return a {@link Geometry}
     */
    public static Geometry kmlToGeometry(String kml) throws ParseException {
        return KML_READER.read(kml);
    }

    /**
     * @param kml a KML-String
     * @param factory a {@link GeometryFactory}
     * @return a {@link Geometry}
     */
    public static Geometry kmlToGeometry(String kml, GeometryFactory factory) throws ParseException {
        return KML_READER.read(kml);
    }
}
