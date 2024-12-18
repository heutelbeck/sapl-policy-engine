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
package io.sapl.geo.schemata;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TraccarSchemata {

    public static final String ACCURACY          = "accuracy";
    public static final String ADDRESS           = "address";
    public static final String ADMINISTRATOR     = "administrator";
    public static final String ALTITUDE          = "altitude";
    public static final String AREA              = "area";
    public static final String ATTRIBUTES        = "attributes";
    public static final String BING_KEY          = "bingKey";
    public static final String CALENDAR_ID       = "calendarId";
    public static final String CATEGORY          = "category";
    public static final String CONTACT           = "contact";
    public static final String COORDINATE_FORMAT = "coordinateFormat";
    public static final String COURSE            = "course";
    public static final String DESCRIPTION       = "description";
    public static final String DEVICE_ID         = "deviceId";
    public static final String DEVICE_LIMIT      = "deviceLimit";
    public static final String DEVICE_READONLY   = "deviceReadonly";
    public static final String DISABLED          = "disabled";
    public static final String EMAIL             = "email";
    public static final String EXPIRATION_TIME   = "expirationTime";
    public static final String FIX_TIME          = "fixTime";
    public static final String FIXED_EMAIL       = "fixedEmail";
    public static final String FORCE_SETTINGS    = "forceSettings";
    public static final String GEOFENCE_IDS      = "geofenceIds";
    public static final String GROUP_ID          = "groupId";
    public static final String ID                = "id";
    public static final String LAST_UPDATED      = "lastUpdated";
    public static final String LATITUDE          = "latitude";
    public static final String LIMIT_COMMANDS    = "limitCommands";
    public static final String LONGITUDE         = "longitude";
    public static final String MAP               = "map";
    public static final String MAP_URL           = "mapUrl";
    public static final String MODEL             = "model";
    public static final String NAME              = "name";
    public static final String NETWORK           = "network";
    public static final String OPEN_ID_ENABLED   = "openIdEnabled";
    public static final String OPEN_ID_FORCE     = "openIdForce";
    public static final String OUTDATED          = "outdated";
    public static final String PASSWORD          = "password";
    public static final String PHONE             = "phone";
    public static final String POI_LAYER         = "poiLayer";
    public static final String POSITION_ID       = "positionId";
    public static final String PROTOCOL          = "protocol";
    public static final String READONLY          = "readonly";
    public static final String REGISTRATION      = "registration";
    public static final String SERVER_TIME       = "serverTime";
    public static final String SPEED             = "speed";
    public static final String STATUS            = "status";
    public static final String UNIQUE_ID         = "uniqueId";
    public static final String USER_LIMIT        = "userLimit";
    public static final String VALID             = "valid";
    public static final String VERSION           = "version";
    public static final String ZOOM              = "zoom";

    public static final String SERVER_SCHEMA = """
            {
               "$id": "https://traccar.org/server.schema.json",
               "$schema": "https://json-schema.org/draft/2020-12/schema",
               "title": "Server",
               "type": "object",
               "properties": {
                 "id":               { "type": "integer" },
                 "attributes ":      { "type": "object" },
                 "registration":     { "type": "boolean" },
                 "readonly":         { "type": "boolean" },
                 "deviceReadonly":   { "type": "boolean" },
                 "map":              { "type": "string" },
                 "bingKey":          { "type": "string" },
                 "mapUrl":           { "type": "string" },
                 "latitude":         { "type": "number" },
                 "longitude":        { "type": "number" },
                 "zoom":             { "type": "integer" },
                 "forceSettings":    { "type": "boolean" },
                 "coordinateFormat": { "type": "string" },
                 "limitCommands":    { "type": "boolean" },
                 "poiLayer":         { "type": "string" },
                 "openIdEnabled":    { "type": "boolean" },
                 "openIdForce":      { "type": "boolean" },
                 "version":          { "type": "string" }
               }
             }
             """;

    public static final String DEVICES_SCHEMA = """
            {
               "$id": "https://traccar.org/devices.schema.json",
               "$schema": "https://json-schema.org/draft/2020-12/schema",
               "title": "Devices",
               "type": "array",
               "items": {
                   "type": "object",
                   "properties": {
                     "id":          { "type": "integer" },
                     "name":        { "type": "string" },
                     "uniqueId":    { "type": "string" },
                     "status":      { "type": "string" },
                     "disabled":    { "type": "boolean" },
                     "lastUpdated": { "type": "string", "format": "date-time" },
                     "positionId":  { "type": "integer" },
                     "groupId":     { "type": "integer" },
                     "phone":       { "type": "string" },
                     "model":       { "type": "string" },
                     "contact":     { "type": "string" },
                     "category":    { "type": "string" },
                     "attributes ": { "type": "object" }
                   }
               }
             }
             """;

    public static final String DEVICE_SCHEMA = """
            {
               "$id": "https://traccar.org/device.schema.json",
               "$schema": "https://json-schema.org/draft/2020-12/schema",
               "title": "Device",
               "type": "object",
               "properties": {
                 "id":          { "type": "integer" },
                 "name":        { "type": "string" },
                 "uniqueId":    { "type": "string" },
                 "status":      { "type": "string" },
                 "disabled":    { "type": "boolean" },
                 "lastUpdated": { "type": "string", "format": "date-time" },
                 "positionId":  { "type": "integer" },
                 "groupId":     { "type": "integer" },
                 "phone":       { "type": "string" },
                 "model":       { "type": "string" },
                 "contact":     { "type": "string" },
                 "category":    { "type": "string" },
                 "attributes ": { "type": "object" }
               }
             }
             """;

    public static final String POSITION_SCHEMA = """
            {
               "$id": "https://traccar.org/position.schema.json",
               "$schema": "https://json-schema.org/draft/2020-12/schema",
               "title": "Position",
               "type": "object",
               "properties": {
                 "id":          { "type": "integer" },
                 "deviceId":    { "type": "integer" },
                 "protocol":    { "type": "string" },
                 "deviceTime":  { "type": "string", "format": "date-time" },
                 "fixTime":     { "type": "string", "format": "date-time" },
                 "serverTime":  { "type": "string", "format": "date-time" },
                 "outdated":    { "type": "boolean" },
                 "valid":       { "type": "boolean" },
                 "latitude":    { "type": "number" },
                 "longitude":   { "type": "number" },
                 "altitude":    { "type": "number" },
                 "speed":       { "type": "number" },
                 "course":      { "type": "number" },
                 "address":     { "type": "string" },
                 "accuracy":    { "type": "number" },
                 "address":     { "type": "string" },
                 "network":     { "type": "object" },
                 "geofenceIds": { "type": "array", "items": { "type" : "integer" } },
                 "attributes ": { "type": "object" }
               }
             }
             """;

    public static final String GEOFENCE_SCHEMA = """
            {
              "$id": "https://traccar.org/geofence.schema.json",
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "id":          { "type": "integer" },
                "name":        { "type": "string" },
                "description": { "type": "string" },
                "area":        { "type": "string" },
                "calendarId":  { "type": "integer" },
                "attributes":  { "type": "object" }
              }
            }
            """;

    public static final String USER_SCHEMA = """
            {
              "$id": "https://traccar.org/user.schema.json",
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "id":                { "type": "integer" },
                "name":              { "type": "string" },
                "email":             { "type": "string" },
                "phone":             { "type": "string" },
                "readonly":          { "type": "boolean" },
                "administrator":     { "type": "boolean" },
                "map":               { "type": "string" },
                "latitude":          { "type": "number" },
                "longitude":         { "type": "number" },
                "zoom":              { "type": "integer" },
                "password":          { "type": "string" },
                "coordinateFormat":  { "type": "string" },
                "disabled":          { "type": "boolean" },
                "expirationTime":    { "type": "string", "format": "date-time" },
                "deviceLimit":       { "type": "integer" },
                "userLimit":         { "type": "integer" },
                "deviceReadonly":    { "type": "boolean" },
                "limitCommands":     { "type": "boolean" },
                "fixedEmail":        { "type": "boolean" },
                "poiLayer":          { "type": "string" },
                "attributes":        { "type": "object" }
              }
            }
            """;

    public static final String GEOFENCES_SCHEMA = """
            {
              "$id": "https://traccar.org/geofences.schema.json",
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "id":          { "type": "integer" },
                    "name":        { "type": "string" },
                    "description": { "type": "string" },
                    "area":        { "type": "string" },
                    "calendarId":  { "type": "integer" },
                    "attributes":  { "type": "object" }
                  }
                }
              }
            """;
}
