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
package io.sapl.test.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("TestDiscoveryHelper tests")
class TestDiscoveryHelperTests {

    private final MockedStatic<FileUtils> fileUtilsMockedStatic = mockStatic(FileUtils.class);

    @AfterEach
    void tearDown() {
        fileUtilsMockedStatic.close();
    }

    @Test
    @DisplayName("returns empty list when no test files found")
    void whenNoTestFiles_thenReturnsEmptyList() {
        var directoryMock = mock(File.class);

        fileUtilsMockedStatic.when(() -> FileUtils.getFile("src/test/resources")).thenReturn(directoryMock);
        fileUtilsMockedStatic.when(() -> FileUtils.listFiles(directoryMock, new String[] { "sapltest" }, true))
                .thenReturn(Collections.emptyList());

        var result = TestDiscoveryHelper.discoverTests();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("discovers test files with correct extension and returns relative paths")
    void whenTestFilesExist_thenReturnsRelativePaths() {
        var directoryMock = mock(File.class);
        fileUtilsMockedStatic.when(() -> FileUtils.getFile("src/test/resources")).thenReturn(directoryMock);

        var file1Mock = mock(File.class);
        var file2Mock = mock(File.class);

        fileUtilsMockedStatic.when(() -> FileUtils.listFiles(directoryMock, new String[] { "sapltest" }, true))
                .thenReturn(List.of(file1Mock, file2Mock));

        var directoryPathMock = mock(Path.class);
        when(directoryMock.toPath()).thenReturn(directoryPathMock);

        var file1PathMock = mock(Path.class);
        when(file1Mock.toPath()).thenReturn(file1PathMock);

        var file2PathMock = mock(Path.class);
        when(file2Mock.toPath()).thenReturn(file2PathMock);

        var file1RelativePathMock = mock(Path.class);
        when(directoryPathMock.relativize(file1PathMock)).thenReturn(file1RelativePathMock);

        var file2RelativePathMock = mock(Path.class);
        when(directoryPathMock.relativize(file2PathMock)).thenReturn(file2RelativePathMock);

        when(file1RelativePathMock.toString()).thenReturn("filePath1");
        when(file2RelativePathMock.toString()).thenReturn("folder/filePath2");

        var result = TestDiscoveryHelper.discoverTests();

        assertThat(result).hasSize(2).containsExactly("filePath1", "folder/filePath2");
    }
}
