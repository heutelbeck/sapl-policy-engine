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
package io.sapl.conformance;

import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.functions.DefaultLibraries;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The function libraries that the function corpus and the function catalogue
 * can cover: the default libraries of this engine, the libraries of the
 * extensions that compute their results from their arguments alone, such as
 * the library of MQTT topics, and the libraries of extensions that depend on
 * this module, such as the geographic library. An extension that depends on
 * this module cannot be a dependency of its tests, so its library is covered
 * where it is on the class path: in the tests of the extension, which reuse
 * the generator and the checks of this module.
 */
@UtilityClass
public class ConformanceLibraries {

    private static final String ERROR_NOT_INSTANTIABLE = "The function library %s cannot be instantiated.";

    /**
     * The libraries of the extensions that depend on this module, by the name
     * of the library and the class that implements it.
     */
    private static final Map<String, String> DEPENDENT_LIBRARIES = Map.of("geo",
            "io.sapl.functions.geo.GeographicFunctionLibrary");

    private static final List<Object> LIBRARIES = Stream
            .of(DefaultLibraries.defaults().stream(), Stream.<Object>of(new MqttFunctionLibrary()),
                    DEPENDENT_LIBRARIES.values().stream().flatMap(name -> instanceOf(name).stream()))
            .flatMap(Function.identity()).toList();

    /**
     * @return the default libraries followed by the pure libraries of the
     * extensions that are on the class path
     */
    static List<Object> libraries() {
        return LIBRARIES;
    }

    /**
     * @return the names of the libraries of extensions that depend on this
     * module and are not on the class path, so that their cases and their
     * entries in the catalogue are checked elsewhere
     */
    public static Set<String> absentLibraries() {
        val absent = new TreeSet<String>();
        DEPENDENT_LIBRARIES.forEach((name, className) -> {
            if (instanceOf(className).isEmpty()) {
                absent.add(name);
            }
        });
        return absent;
    }

    /**
     * @param catalogue a function catalogue
     * @return the catalogue without the entries of the libraries that are not
     * on the class path
     */
    public static JsonNode withoutAbsentLibraries(JsonNode catalogue) {
        val absent    = absentLibraries();
        val libraries = JsonNodeFactory.instance.arrayNode();
        catalogue.forEach(library -> {
            if (!absent.contains(library.get("name").asString())) {
                libraries.add(library);
            }
        });
        return libraries;
    }

    private static Optional<Object> instanceOf(String className) {
        try {
            return Optional.of(Class.forName(className).getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException(ERROR_NOT_INSTANTIABLE.formatted(className), e);
        }
    }
}
