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
package io.sapl.geo.common;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.interpreter.Val;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class TestBase {

    @TempDir
    protected static Path tempDir;

    protected Val            point;
    protected Val            polygon;
    protected SourceProvider source = new SourceProvider();

    protected void writePdpJson(String json) throws IOException {
        final var filePath = Path.of(tempDir.toAbsolutePath().toString(), "pdp.json");
        try (final var writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    protected void copyToTemp(String resourcePath) throws IOException, URISyntaxException {
        final var url  = TestBase.class.getResource(resourcePath);
        final var file = new File(url.toURI());
        FileUtils.copyFile(file, new File(tempDir.toAbsolutePath().toString(), file.toPath().getFileName().toString()));
    }

}
