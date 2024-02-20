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
package io.sapl.test.utils;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.sapl.test.SaplTestException;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ClasspathHelper {

    private static final String DEFAULT_PATH = "policies/";

    private static final String ERROR_MAIN_MESSAGE = "Error finding %s or %s on the classpath!";

    public static Path findPathOnClasspath(@NonNull ClassLoader loader, @NonNull String path) {

        // try path as specified
        URL url = loader.getResource(path);
        if (url != null) {
            return getResourcePath(url);
        }

        // try DEFAULT_PATH + specified path
        String defaultPath        = DEFAULT_PATH + path;
        URL    urlFromDefaultPath = loader.getResource(defaultPath);
        if (urlFromDefaultPath != null) {
            return getResourcePath(urlFromDefaultPath);
        }

        // nothing found -> throw useful exception
        StringBuilder errorMessage = new StringBuilder(String.format(ERROR_MAIN_MESSAGE, path, defaultPath));
        if (loader instanceof URLClassLoader urlClassLoader) {
            errorMessage.append(System.lineSeparator()).append(System.lineSeparator())
                    .append("We tried the following paths:").append(System.lineSeparator());
            URL[] classpathElements = urlClassLoader.getURLs();
            for (URL classpathElement : classpathElements) {
                errorMessage.append("    - ").append(classpathElement);
            }
        }
        throw new SaplTestException(errorMessage.toString());

    }

    @SneakyThrows
    private static Path getResourcePath(URL url) {
        if ("jar".equals(url.getProtocol())) {
            throw new SaplTestException("Not supporting reading files from jar during test execution!");
        }

        return Paths.get(url.toURI());
    }

}
