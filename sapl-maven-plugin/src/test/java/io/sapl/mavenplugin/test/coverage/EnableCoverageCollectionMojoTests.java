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
package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnableCoverageCollectionMojoTests {

    private Log                          log;
    private EnableCoverageCollectionMojo mojo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        log  = mock(Log.class);
        mojo = new EnableCoverageCollectionMojo();
        mojo.setLog(log);

        setField(mojo, "coverageEnabled", true);
        setField(mojo, "projectBuildDir", tempDir.toString());
    }

    @Test
    void whenCoverageDisabled_thenSkipsExecution() throws Exception {
        setField(mojo, "coverageEnabled", false);

        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void whenCoverageEnabled_thenDeletesOutputDirectory() {
        try (var pathHelperMock = mockStatic(PathHelper.class)) {
            pathHelperMock.when(() -> PathHelper.resolveBaseDir(any(), any(), any())).thenReturn(tempDir);

            assertDoesNotThrow(mojo::execute);
        }
    }

    @Test
    void whenDeleteFails_thenThrowsMojoException() {
        try (var pathHelperMock = mockStatic(PathHelper.class)) {
            try (var fileUtilsMock = mockStatic(FileUtils.class)) {
                pathHelperMock.when(() -> PathHelper.resolveBaseDir(any(), any(), any())).thenReturn(Paths.get("tmp"));
                fileUtilsMock.when(() -> FileUtils.deleteDirectory(any())).thenThrow(new IOException("Delete failed."));

                assertThrows(MojoExecutionException.class, mojo::execute);
            }
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
