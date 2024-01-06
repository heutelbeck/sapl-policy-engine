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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JarUtil {

    public URL inferUrlOfResourcesPath(Class<?> clazz, String path) {
        var url = clazz.getResource(path);
        if (url == null)
            throw new IllegalStateException(
                    "Folder in application resources is either empty or not present at all. Path:" + path);
        return url;
    }

    public String getJarFilePath(URL url) {
        return url.toString().split("!")[0].substring("jar:file:".length());
    }

    @SneakyThrows
    public String readStringFromZipEntry(ZipFile jarFile, ZipEntry entry) {
        var bis    = new BufferedInputStream(jarFile.getInputStream(entry));
        var result = IOUtils.toString(bis, StandardCharsets.UTF_8);
        bis.close();
        return result;
    }

}
