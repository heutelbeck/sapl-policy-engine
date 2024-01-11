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
package io.sapl.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import lombok.experimental.UtilityClass;

/**
 * Utility class for creating plain JAR archives.
 */
@UtilityClass
public class JarCreator {

    public static URL createPoliciesInJar(String jarFolderReference, Path tempDir)
            throws IOException, URISyntaxException {
        return createJarFromResource("policies_in_jar.jar", "/setups/policies_in_jar", jarFolderReference, tempDir);
    }

    public static URL createBrokenPoliciesInJar(String jarFolderReference, Path tempDir)
            throws IOException, URISyntaxException {
        return createJarFromResource("broken_policies_in_jar.jar", "/setups/broken_config", jarFolderReference,
                tempDir);
    }

    private static void createJar(Path jarFilePath, URI[] sourcePaths) throws IOException {
        try (var jos = new JarOutputStream(Files.newOutputStream(jarFilePath))) {
            for (var sourcePath : sourcePaths) {
                var source = Path.of(sourcePath);
                addFileToJar(source, source, jos);
            }
        }
    }

    private static URL createJarFromResource(String jarName, String resourcePath, String jarFolderReference,
            Path tempDir) throws IOException, URISyntaxException {
        var path = tempDir.resolve(jarName);
        JarCreator.createJar(path, new URI[] { JarCreator.class.getResource(resourcePath).toURI() });
        return new URL("jar:" + path.toUri().toURL() + jarFolderReference);
    }

    private static void addFileToJar(Path root, Path source, JarOutputStream jos) throws IOException {
        var relativePath = root.toUri().relativize(source.toUri()).getPath();

        if (Files.isDirectory(source)) {
            var jarEntry = new JarEntry(relativePath);
            jos.putNextEntry(jarEntry);
            jos.closeEntry();

            try (Stream<Path> paths = Files.list(source)) {
                paths.forEach(path -> {
                    try {
                        addFileToJar(root, path, jos);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } else {
            var jarEntry = new JarEntry(relativePath);
            jos.putNextEntry(jarEntry);

            Files.copy(source, jos);

            jos.closeEntry();
        }
    }
}
