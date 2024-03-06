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
package io.sapl.geo.connection.fileimport;

import java.io.BufferedReader;
import java.io.FileReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.connection.traccar.TraccarSessionHandler;
import io.sapl.geo.connection.traccar.TraccarSessionManager;
import io.sapl.geo.connection.traccar.TraccarSocketManager;
import io.sapl.geo.pip.GeoPipResponseFormat;
import reactor.core.publisher.Flux;

public class FileLoader extends ConnectionBase {

    private static final String PATH = "path";
    private BufferedReader      reader;

    private FileLoader(String path, ObjectMapper mapper) throws Exception {

        reader = new BufferedReader(new FileReader(path));

    }

    public static FileLoader getNew(String path, ObjectMapper mapper) throws Exception {

        return new FileLoader(path, mapper);
    }

//    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {
//
//        try {
//            var fileLoader = getNew(getPath(settings), mapper);
//            return fileLoader.getFlux(getResponseFormat(settings, mapper), mapper).map(Val::of).onErrorResume(e -> {
//                return Flux.just(Val.error(e));
//            });
//        } catch (Exception e) {
//            return Flux.just(Val.error(e));
//        }
//
//    }

//    public Flux<ObjectNode> getFlux(GeoPipResponseFormat format, ObjectMapper mapper) {
//
//        return Flux.just(null);
//    }
//
//    private void readFile(String path) {
//    }

    private static String getPath(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PATH)) {
            return requestSettings.findValue(PATH).asText();
        } else {
            throw new PolicyEvaluationException("No filepath found");

        }

    }

}
