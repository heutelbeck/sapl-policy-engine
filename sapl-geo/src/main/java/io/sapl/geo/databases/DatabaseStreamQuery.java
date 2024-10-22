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
package io.sapl.geo.databases;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeoProjector;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.JsonConverter;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geo.shared.ConnectionBase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

public final class DatabaseStreamQuery extends ConnectionBase {

    private static final String DATABASE           = "dataBase";
    private static final String TABLE              = "table";
    private static final String GEOCOLUMN          = "geoColumn";
    private static final String COLUMNS            = "columns";
    private static final String WHERE              = "where";
    private static final String DEFAULTCRS         = "defaultCRS";
    private static final String SINGLE_RESULT      = "singleResult";
    private static final String SRC_LATITUDE_FIRST = "srcLatitudeFirst";
    private static final String EPSG               = "EPSG:";
    private static final String PORT               = "port";
    private String[]            selectColumns;
    private boolean             singleResult;
    private DataBaseTypes       dataBaseType;
    private ConnectionFactory   connectionFactory;
    private JsonNode            auth;

    /**
     * @param auth a {@link JsonNode} containing the settings for authorization
     * @param mapper a {@link ObjectMapper}
     */
    public DatabaseStreamQuery(JsonNode auth, ObjectMapper mapper, DataBaseTypes dataBaseType) {
        this.auth         = auth;
        this.dataBaseType = dataBaseType;
        this.mapper       = mapper;
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux} of {@link Val}
     */
    public Flux<Val> sendQuery(JsonNode settings) {
        if (dataBaseType == DataBaseTypes.MYSQL) {
            createMySqlConnectionFactory(auth, getPort(auth));
        } else {
            createPostgresqlConnectionFactory(auth, getPort(auth));
        }

        selectColumns = getColumns(settings, mapper);
        singleResult  = getSingleResult(settings);
        return createConnection(getResponseFormat(settings, mapper),
                buildSql(getGeoColumn(settings), selectColumns, getTable(settings), getWhere(settings)),
                getDefaultCRS(settings), longOrDefault(settings, REPEAT_TIMES_CONST, DEFAULT_REPETITIONS_CONST),
                longOrDefault(settings, POLLING_INTERVAL_CONST, DEFAULT_POLLING_INTERVALL_MS_CONST),
                getSourceLatitudeFirst(settings), getLatitudeFirst(settings)).map(Val::of)
                .onErrorResume(e -> Flux.just(Val.error(e.getMessage())));
    }

    private Flux<JsonNode> createConnection(GeoPipResponseFormat format, String sql, int defaultCrs, long repeatTimes,
            long pollingInterval, boolean srcLatitudeFirst, boolean latitudeFirst) {
        if (singleResult) {
            return poll(getResults(sql, format, defaultCrs, srcLatitudeFirst, latitudeFirst).next(), repeatTimes,
                    pollingInterval);
        } else {
            return poll(collectMultipleResults(getResults(sql, format, defaultCrs, srcLatitudeFirst, latitudeFirst)),
                    repeatTimes, pollingInterval);
        }
    }

    private Flux<JsonNode> getResults(String sql, GeoPipResponseFormat format, int defaultCrs, boolean srcLatitudeFirst,
            boolean latitudeFirst) {
        return Flux.usingWhen(connectionFactory.create(), conn -> Flux.from(conn.createStatement(sql).execute())
                .flatMap(result -> result.map((row, rowMetadata) -> {
                    try {
                        return mapResult(row, format, defaultCrs, srcLatitudeFirst, latitudeFirst);
                    } catch (MismatchedDimensionException | JsonProcessingException | ParseException | FactoryException
                            | TransformException e) {
                        throw new PolicyEvaluationException(e);
                    }
                })), Connection::close);
    }

    private JsonNode mapResult(Row row, GeoPipResponseFormat format, int defaultCrs, boolean srcLatitudeFirst,
            boolean latitudeFirst) throws MismatchedDimensionException, JsonProcessingException, ParseException,
            FactoryException, TransformException {
        final var resultNode = mapper.createObjectNode();
        JsonNode  geoNode;
        final var resValue   = row.get("res", String.class);
        var       srid       = row.get("srid", Integer.class);
        if (srid == null || srid == 0) {
            srid = defaultCrs;
        }
        geoNode = convertResponse(resValue, format, srid, srcLatitudeFirst, latitudeFirst);
        resultNode.put("srid", srid);
        resultNode.set("geo", geoNode);
        for (final var column : selectColumns) {
            resultNode.put(column, row.get(column, String.class));
        }
        return resultNode;
    }

    private JsonNode convertResponse(String in, GeoPipResponseFormat format, int srid, boolean srcLatitudeFirst,
            boolean latitudeFirst) throws ParseException, FactoryException, MismatchedDimensionException,
            TransformException, JsonProcessingException {
        var       res          = (JsonNode) mapper.createObjectNode();
        final var crs          = EPSG + srid;
        var       geo          = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
        final var geoProjector = new GeoProjector(crs, !srcLatitudeFirst, crs, !latitudeFirst);
        geo = geoProjector.project(geo);
        res = switch (format) {
            case GEOJSON -> GeometryConverter.geometryToGeoJsonNode(geo).get();
            case WKT     -> GeometryConverter.geometryToWKT(geo).get();
            case GML     -> GeometryConverter.geometryToGML(geo).get();
            case KML     -> GeometryConverter.geometryToKML(geo).get();
            };

        return res;
    }

    private Mono<JsonNode> collectMultipleResults(Flux<JsonNode> resultFlux) {
        return resultFlux.collect(ArrayList::new, List::add).map(results -> {
            final var arrayNode = mapper.createArrayNode();
            for (final var node : results) {
                arrayNode.add((JsonNode) node);
            }
            return (JsonNode) arrayNode;
        });
    }

    private Flux<JsonNode> poll(Mono<JsonNode> mono, long repeatTimes, long pollingInterval) {
        return mono.repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))));
    }

    private final void createPostgresqlConnectionFactory(JsonNode auth, int port) {
        connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder().username(getUser(auth)).password(getPassword(auth))
                        .host(getServer(auth)).port(port).database(getDataBaseName(auth)).build());
    }

    private final void createMySqlConnectionFactory(JsonNode auth, int port) {
        connectionFactory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder().username(getUser(auth))
                .password(getPassword(auth)).host(getServer(auth)).port(port).database(getDataBaseName(auth))
                .serverZoneId(ZoneId.of("UTC")).build());
    }

    private String buildSql(String geoColumn, String[] columns, String table, String where) {
        final var frmt    = "ST_AsGeoJSON";
        final var builder = new StringBuilder();
        for (final var c : columns) {
            builder.append(String.format(", %s AS %s", c, c));
        }
        final var clms = builder.toString();
        var       str  = "SELECT %s(%s) AS res, ST_SRID(%s) AS srid%s FROM %s %s";
        if (singleResult) {
            str = str.concat(" LIMIT 1");
        }
        return String.format(str, frmt, geoColumn, geoColumn, clms, table, where);
    }

    private String getDataBaseName(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DATABASE)) {
            return requestSettings.findValue(DATABASE).asText();
        } else {
            throw new PolicyEvaluationException("No database-name found");
        }
    }

    private String getTable(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(TABLE)) {
            return requestSettings.findValue(TABLE).asText();
        } else {
            throw new PolicyEvaluationException("No table-name found");
        }
    }

    private String getGeoColumn(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(GEOCOLUMN)) {
            return requestSettings.findValue(GEOCOLUMN).asText();
        } else {
            throw new PolicyEvaluationException("No geoColumn-name found");
        }
    }

    private String[] getColumns(JsonNode requestSettings, ObjectMapper mapper) {
        if (requestSettings.has(COLUMNS)) {
            final var columns = requestSettings.findValue(COLUMNS);
            if (columns.isArray()) {
                return mapper.convertValue((ArrayNode) columns, String[].class);
            }
            return new String[] { columns.asText() };
        } else {
            return new String[] {};
        }
    }

    private String getWhere(JsonNode requestSettings) {
        if (requestSettings.has(WHERE)) {
            return String.format("WHERE %s", requestSettings.findValue(WHERE).asText());
        } else {
            return "";
        }
    }

    private int getDefaultCRS(JsonNode requestSettings) {
        if (requestSettings.has(DEFAULTCRS)) {
            return requestSettings.findValue(DEFAULTCRS).asInt();
        } else {
            return 4326;
        }
    }

    private boolean getSingleResult(JsonNode requestSettings) {
        if (requestSettings.has(SINGLE_RESULT)) {
            return requestSettings.findValue(SINGLE_RESULT).asBoolean();
        } else {
            return false;
        }
    }

    private boolean getSourceLatitudeFirst(JsonNode requestSettings) {
        if (requestSettings.has(SRC_LATITUDE_FIRST)) {
            return requestSettings.findValue(SRC_LATITUDE_FIRST).asBoolean();
        } else {
            return false;
        }
    }

    private long longOrDefault(JsonNode requestSettings, String fieldName, long defaultValue) {
        if (requestSettings.has(fieldName)) {
            final var value = requestSettings.findValue(fieldName);

            if (!value.isNumber())
                throw new PolicyEvaluationException(fieldName + " must be an integer, but was: " + value.getNodeType());
            return value.asLong();
        }
        return defaultValue;
    }

    private final int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) { // called in constructor
            return requestSettings.findValue(PORT).asInt();
        } else {
            if (dataBaseType == DataBaseTypes.MYSQL) {
                return 3306;
            } else {
                return 5432;
            }
        }
    }
}
