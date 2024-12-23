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
package io.sapl.geo.library;

import lombok.Getter;

public enum CrsConst {

    WGS84_CRS(1, "EPSG:4326"), // WGS84
    WEB_MERCATOR_CRS(2, "EPSG:3857"), // WebMercator
    ED50_CRS(3, "EPSG:23032"), DHDN_2_CRS(4, "EPSG:31466"), DHDN_3_CRS(5, "EPSG:31467"), DHDN_4_CRS(6, "EPSG:31468"),
    DHDN_5_CRS(7, "EPSG:31469");

    @Getter
    private final Integer key;
    @Getter
    private final String  value;

    CrsConst(Integer key, String value) {
        this.key   = key;
        this.value = value;
    }
}
