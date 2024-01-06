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
package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class PathHelperTests {

    private Log log;

    @BeforeEach
    void setup() {
        log = mock(Log.class);
    }

    @Test
    void test_customConfigBaseDir() {

        String configBaseDir   = "test";
        String projectBuildDir = "target";

        Path expectedPath = Path.of("test", "sapl-coverage");

        Path result = PathHelper.resolveBaseDir(configBaseDir, projectBuildDir, this.log);

        assertEquals(expectedPath, result);
    }

    @Test
    void test_customConfigBaseDir_Empty() {

        String configBaseDir   = "";
        String projectBuildDir = "target";

        Path expectedPath = Path.of("target", "sapl-coverage");

        Path result = PathHelper.resolveBaseDir(configBaseDir, projectBuildDir, this.log);

        assertEquals(expectedPath, result);
    }

    @Test
    void test_customConfigBaseDir_Null() {

        String projectBuildDir = "target";

        Path expectedPath = Path.of("target", "sapl-coverage");

        Path result = PathHelper.resolveBaseDir(null, projectBuildDir, this.log);

        assertEquals(expectedPath, result);
    }

    @Test
    void test_createFile_FileExists() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(Boolean.TRUE);
            assertDoesNotThrow(() -> PathHelper.createFile(Path.of("test.txt")));
            mockedFiles.verify(() -> Files.createFile(any()), never());
            mockedFiles.verify(() -> Files.createDirectories(any()), never());
        }
    }

    @Test
    void test_createFile_ParentExists() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            Path file = mock(Path.class);
            when(file.getParent()).thenReturn(null);
            assertDoesNotThrow(() -> PathHelper.createFile(file));
            mockedFiles.verify(() -> Files.createFile(any()), times(1));
            mockedFiles.verify(() -> Files.createDirectories(any()), never());
        }
    }

    @Test
    void test_createFile_ParentNotExisting() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            Path file      = mock(Path.class);
            Path parentDir = mock(Path.class);
            when(file.getParent()).thenReturn(parentDir);
            assertDoesNotThrow(() -> PathHelper.createFile(file));
            mockedFiles.verify(() -> Files.createFile(any()), times(1));
            mockedFiles.verify(() -> Files.createDirectories(any()), times(1));
        }
    }

    @Test
    void test_createParentDirs_FileExists() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(Boolean.TRUE);
            assertDoesNotThrow(() -> PathHelper.createParentDirs(Path.of("test.txt")));
            mockedFiles.verify(() -> Files.createDirectories(any()), never());
        }
    }

    @Test
    void test_createParentDirs_ParentExists() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            Path file = mock(Path.class);
            when(file.getParent()).thenReturn(null);
            assertDoesNotThrow(() -> PathHelper.createParentDirs(file));
            mockedFiles.verify(() -> Files.createDirectories(any()), never());
        }
    }

    @Test
    void test_createParentDirs_ParentNotExisting() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            Path file      = mock(Path.class);
            Path parentDir = mock(Path.class);
            when(file.getParent()).thenReturn(parentDir);
            assertDoesNotThrow(() -> PathHelper.createParentDirs(file));
            mockedFiles.verify(() -> Files.createDirectories(any()), times(1));
        }
    }

}
