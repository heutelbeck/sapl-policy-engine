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

import java.util.List;
import java.util.stream.Stream;

/**
 * The function libraries that the function corpus and the function catalogue
 * can cover: the default libraries of this engine and the libraries of the
 * extensions that compute their results from their arguments alone, such as
 * the library of MQTT topics.
 */
@UtilityClass
class ConformanceLibraries {

    private static final List<Object> LIBRARIES = Stream
            .concat(DefaultLibraries.defaults().stream(), Stream.<Object>of(new MqttFunctionLibrary())).toList();

    /**
     * @return the default libraries followed by the pure libraries of the
     * extensions
     */
    static List<Object> libraries() {
        return LIBRARIES;
    }
}
