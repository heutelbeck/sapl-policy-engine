/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.test.lang.SaplTestException;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for locating and reading resources from the classpath.
 */
@UtilityClass
public class ClasspathHelper {

    private static final String DEFAULT_PATH = "policies/";

    private static final String ERROR_MAIN_MESSAGE = "Error finding %s or %s on the classpath!";
    private static final String ERROR_POLICY_LOAD  = "Failed to load policy from classpath: %s.";

    /**
     * Reads a SAPL policy source from the classpath.
     * <p>
     * Tries to locate the file at the specified path first, then at the default
     * path (policies/) if not found.
     *
     * @param resourcePath the path to the policy file
     * @return the policy source code as a string
     * @throws SaplTestException if the policy cannot be loaded
     */
    public static String readPolicyFromClasspath(@NonNull String resourcePath) {
        var loader = Thread.currentThread().getContextClassLoader();

        try (InputStream inputStream = findResourceAsStream(loader, resourcePath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SaplTestException(ERROR_POLICY_LOAD.formatted(resourcePath), exception);
        }
    }

    private static InputStream findResourceAsStream(ClassLoader loader, String path) {
        var inputStream = loader.getResourceAsStream(path);
        if (inputStream != null) {
            return inputStream;
        }

        var defaultPath   = DEFAULT_PATH + path;
        var defaultStream = loader.getResourceAsStream(defaultPath);
        if (defaultStream != null) {
            return defaultStream;
        }

        throw new SaplTestException(ERROR_MAIN_MESSAGE.formatted(path, defaultPath));
    }

    /**
     * Finds a path on the classpath.
     *
     * @param loader the class loader to use
     * @param path the resource path
     * @return the Path to the resource
     * @throws SaplTestException if the resource cannot be found
     */
    public static Path findPathOnClasspath(@NonNull ClassLoader loader, @NonNull String path) {

        var url = loader.getResource(path);
        if (url != null) {
            return getResourcePath(url);
        }

        var defaultPath        = DEFAULT_PATH + path;
        var urlFromDefaultPath = loader.getResource(defaultPath);
        if (urlFromDefaultPath != null) {
            return getResourcePath(urlFromDefaultPath);
        }

        var errorMessage = new StringBuilder(ERROR_MAIN_MESSAGE.formatted(path, defaultPath));
        if (loader instanceof URLClassLoader urlClassLoader) {
            errorMessage.append(System.lineSeparator()).append(System.lineSeparator())
                    .append("We tried the following paths:").append(System.lineSeparator());
            var classpathElements = urlClassLoader.getURLs();
            for (var classpathElement : classpathElements) {
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
