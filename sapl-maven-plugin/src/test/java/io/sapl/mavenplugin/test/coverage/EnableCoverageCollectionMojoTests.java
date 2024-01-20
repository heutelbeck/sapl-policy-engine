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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class EnableCoverageCollectionMojoTests extends AbstractMojoTestCase {

    private Log log;

    @BeforeEach
    void setup() throws Exception {
        super.setUp();

        log = Mockito.mock(Log.class);
    }

    @Test
    void test_disableCoverage() throws Exception {

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_withoutProject_coverageDisabled.xml");
        var  mojo = (EnableCoverageCollectionMojo) lookupMojo("enable-coverage-collection", pom.toFile());
        mojo.setLog(this.log);

        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void test() throws Exception {
        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_withoutProject.xml");
        var  mojo = (EnableCoverageCollectionMojo) lookupMojo("enable-coverage-collection", pom.toFile());
        mojo.setLog(this.log);

        try (MockedStatic<PathHelper> pathHelper = Mockito.mockStatic(PathHelper.class)) {
            pathHelper.when(() -> PathHelper.resolveBaseDir(any(), any(), any())).thenReturn(Paths.get("tmp"));
            assertDoesNotThrow(mojo::execute);
        }
    }

    @Test
    void when_deleteFails_MojoException() throws Exception {
        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_withoutProject.xml");
        var  mojo = (EnableCoverageCollectionMojo) lookupMojo("enable-coverage-collection", pom.toFile());
        mojo.setLog(this.log);

        try (MockedStatic<PathHelper> pathHelper = Mockito.mockStatic(PathHelper.class)) {
            try (MockedStatic<FileUtils> fileUtil = Mockito.mockStatic(FileUtils.class)) {
                pathHelper.when(() -> PathHelper.resolveBaseDir(any(), any(), any())).thenReturn(Paths.get("tmp"));
                fileUtil.when(() -> FileUtils.deleteDirectory(any())).thenThrow(new IOException());
                assertThrows(MojoExecutionException.class, mojo::execute);
            }
        }
    }

}
