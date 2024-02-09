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
package io.sapl.test.coverage.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageFilesUtilityTests {

    @Test
    void test_DirectoryNotEmptyException(@TempDir Path tempDir) throws IOException {
        var reader          = CoverageAPIFactory.constructCoverageHitReader(tempDir);
        var pathToErrorFile = tempDir.resolve("hits").resolve("_policySetHits.txt").resolve("test.txt");
        var parent          = pathToErrorFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            Files.createFile(pathToErrorFile);
        }
        assertDoesNotThrow(reader::cleanCoverageHitFiles);
    }

    @Test
    void test_FileAlreadyExists(@TempDir Path tempDir) throws IOException {
        Path pathToErrorFile = tempDir.resolve("hits").resolve("_policySetHits.txt");
        var  parent          = pathToErrorFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            Files.createFile(pathToErrorFile);
        }
        CoverageHitRecorder recorder = new CoverageHitAPIFile(tempDir);
        assertDoesNotThrow(recorder::createCoverageHitFiles);
    }

    @Test
    void test_NoParent() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(Mockito.any())).thenReturn(Boolean.FALSE);
            Path path = Mockito.mock(Path.class);
            when(path.getParent()).thenReturn(null);
            when(path.resolve(Mockito.anyString())).thenReturn(path);
            CoverageHitRecorder recorder = new CoverageHitAPIFile(path);
            assertDoesNotThrow(recorder::createCoverageHitFiles);
            recorder.cleanCoverageHitFiles();
        }
    }

    @Test
    void test_ThrowsIOException_OnCreateCoverageFiles(@TempDir Path tempDir) {
        CoverageHitRecorder recorder = new CoverageHitAPIFile(tempDir);
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createDirectories(Mockito.any())).thenThrow(IOException.class);
            assertDoesNotThrow(recorder::createCoverageHitFiles);
        }
    }

    @Test
    void test_ThrowsIOException_OnRecordHit(@TempDir Path tempDir) {
        CoverageHitRecorder recorder = new CoverageHitAPIFile(tempDir);
        recorder.createCoverageHitFiles();
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.lines(Mockito.any())).thenThrow(IOException.class);
            assertDoesNotThrow(() -> recorder.recordPolicySetHit(new PolicySetHit("")));
        }
        recorder.cleanCoverageHitFiles();
    }

    @Test
    void test_NullFilePath() {
        Path path = Mockito.mock(Path.class);
        when(path.getParent()).thenReturn(null);
        when(path.resolve(Mockito.anyString())).thenReturn(path).thenReturn(null).thenReturn(path).thenReturn(null)
                .thenReturn(path).thenReturn(null);
        var recorder = new CoverageHitAPIFile(path);
        var hit      = new PolicySetHit("");
        assertThrows(NullPointerException.class, () -> recorder.recordPolicySetHit(hit));
    }

}
