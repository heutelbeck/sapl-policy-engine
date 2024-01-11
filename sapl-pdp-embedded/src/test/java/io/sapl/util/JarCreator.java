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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;

import lombok.experimental.UtilityClass;

/**
 * Utility class for creating plain JAR archives.
 */
@UtilityClass
public class JarCreator {

    public static URL createPoliciesInJar(String jarFolderReference, Path tempDir) throws IOException {
        return createJarFromResource("policies_in_jar.jar", "/setups/policies_in_jar", jarFolderReference, tempDir);
    }

    public static URL createBrokenPoliciesInJar(String jarFolderReference, Path tempDir) throws IOException {
        return createJarFromResource("broken_policies_in_jar.jar", "/setups/broken_config", jarFolderReference,
                tempDir);
    }

    private static void createJar(String jarFilePath, String[] sourcePaths) throws IOException {
        try (var fos = new FileOutputStream(jarFilePath);
                var bos = new BufferedOutputStream(fos);
                var jos = new JarOutputStream(bos)) {

            for (String sourcePath : sourcePaths) {
                var source = Path.of(sourcePath);
                addFileToJar(source, source, jos);
            }
        }
    }

    private static URL createJarFromResource(String jarName, String resourcePath, String jarFolderReference,
            Path tempDir) throws IOException {
        var path = tempDir + "/" + jarName;
        JarCreator.createJar(path, new String[] { JarCreator.class.getResource(resourcePath).getPath() });
        return new URL("jar:" + Paths.get(path).toUri().toURL() + jarFolderReference);
    }

    private static void addFileToJar(Path root, Path source, JarOutputStream jos) throws IOException {
        var relativePath = root.toUri().relativize(source.toUri()).getPath();

        if (Files.isDirectory(source)) {
            if (!relativePath.isEmpty() && !relativePath.endsWith("/")) {
                relativePath += "/";
            }
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
