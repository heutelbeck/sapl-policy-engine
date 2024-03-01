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
package io.sapl.geo.connection.traccar;

import java.sql.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraccarSession {

    private int     id;
    private Object  attributes;
    private String  name;
    private String  login;
    private String  email;
    private String  phone;
    private boolean readonly;
    private boolean administrator;
    private String  map;
    private long    latitude;
    private long    longitude;
    private int     zoom;
    private String  password;
    private Boolean twelveHourFormat;
    private String  coordinateFormat;
    private Boolean disabled;
    private Date    expirationTime;
    private int     deviceLimit;
    private int     userLimit;
    private boolean deviceReadonly;
    private boolean limitCommands;
    private boolean disableReports;
    private boolean fixedEmail;
    private String  poiLayer;

}
