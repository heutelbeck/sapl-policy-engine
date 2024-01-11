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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Utility class for creating plain JAR archives.
 */
public final class JarCreator {

    public static URL createPoliciesInJar(String jarFolderReference) throws IOException {
        var path = System.getProperty("java.io.tmpdir") + "/policies_in_jar.jar";
        JarCreator.createJar(path, new String[] { JarCreator.class.getResource("/setups/policies_in_jar").getPath() });
        return new URL("jar:" + Paths.get(path).toUri().toURL() + jarFolderReference);
    }

    public static URL createBrokenPoliciesInJar(String jarFolderReference) throws IOException {
        var path = System.getProperty("java.io.tmpdir") + "/broken_policies_in_jar.jar";
        JarCreator.createJar(path, new String[] { JarCreator.class.getResource("/setups/broken_config").getPath() });
        return new URL("jar:" + Paths.get(path).toUri().toURL() + jarFolderReference);
    }

    public static void createJar(String jarFilePath, String[] sourcePaths) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(jarFilePath);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                JarOutputStream jos = new JarOutputStream(bos)) {

            for (String sourcePath : sourcePaths) {
                File source = new File(sourcePath);
                addFileToJar(source, source, jos);
            }
        }
    }

    private static void addFileToJar(File root, File source, JarOutputStream jos) throws IOException {
        String relativePath = root.toURI().relativize(source.toURI()).getPath();

        if (source.isDirectory()) {
            if (!relativePath.isEmpty() && !relativePath.endsWith("/")) {
                relativePath += "/";
            }
            JarEntry jarEntry = new JarEntry(relativePath);
            jos.putNextEntry(jarEntry);
            jos.closeEntry();

            for (File file : Objects.requireNonNull(source.listFiles())) {
                addFileToJar(root, file, jos);
            }
        } else {
            JarEntry jarEntry = new JarEntry(relativePath);
            jos.putNextEntry(jarEntry);

            try (FileInputStream fis = new FileInputStream(source);
                    BufferedInputStream bis = new BufferedInputStream(fis)) {
                byte[] buffer = new byte[1024];
                int    bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
            }

            jos.closeEntry();
        }
    }
}
