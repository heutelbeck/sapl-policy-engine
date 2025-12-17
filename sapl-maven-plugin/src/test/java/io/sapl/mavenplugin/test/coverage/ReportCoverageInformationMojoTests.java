/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.pdp.Decision;
import io.sapl.test.coverage.CoverageWriter;
import io.sapl.test.coverage.TestCoverageRecord;

class ReportCoverageInformationMojoTests {

    private Log                           log;
    private ReportCoverageInformationMojo mojo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        log  = mock(Log.class);
        mojo = new ReportCoverageInformationMojo();
        mojo.setLog(log);

        setField(mojo, "coverageEnabled", true);
        setField(mojo, "enableHtmlReport", false);
        setField(mojo, "enableSonarReport", false);
        // Set outputDir to bypass MavenProject dependency
        setField(mojo, "outputDir", tempDir.toString());
    }

    @Test
    void whenCoverageDisabled_thenFails() throws Exception {
        setField(mojo, "coverageEnabled", false);

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void whenSkipTestsWithFailOnDisabled_thenFails() throws Exception {
        writeCoverageData(tempDir.resolve("sapl-coverage"));

        mojo.setSkipTests(true);
        mojo.setFailOnDisabledTests(true);

        assertThrows(MojoFailureException.class, mojo::execute);
        verify(log, atLeastOnce())
                .error("Tests were skipped, but the sapl-maven-plugin is configured to enforce tests to be run.");
    }

    @Test
    void whenSkipTestsWithoutFailOnDisabled_thenSkips() throws Exception {
        writeCoverageData(tempDir.resolve("sapl-coverage"));

        mojo.setSkipTests(true);
        mojo.setFailOnDisabledTests(false);

        assertDoesNotThrow(mojo::execute);
        verify(log, atLeastOnce()).info(contains("Tests disabled"));
    }

    @Test
    void whenMavenTestSkipWithFailOnDisabled_thenFails() throws Exception {
        writeCoverageData(tempDir.resolve("sapl-coverage"));

        mojo.setMavenTestSkip(true);
        mojo.setFailOnDisabledTests(true);

        assertThrows(MojoFailureException.class, mojo::execute);
        verify(log, atLeastOnce())
                .error("Tests were skipped, but the sapl-maven-plugin is configured to enforce tests to be run.");
    }

    @Test
    void whenMavenTestSkipWithoutFailOnDisabled_thenSkips() throws Exception {
        writeCoverageData(tempDir.resolve("sapl-coverage"));

        mojo.setMavenTestSkip(true);
        mojo.setFailOnDisabledTests(false);

        assertDoesNotThrow(mojo::execute);
        verify(log, atLeastOnce()).info(contains("Tests disabled"));
    }

    @Test
    void whenCoverageFileMissing_thenFails() throws Exception {
        assertThrows(MojoFailureException.class, mojo::execute);
        verify(log, atLeastOnce()).error(contains("Coverage file not found"));
    }

    @Test
    void whenCoverageDataValid_thenSucceeds() throws Exception {
        writeCoverageData(tempDir.resolve("sapl-coverage"));

        assertDoesNotThrow(mojo::execute);
    }

    private void writeCoverageData(Path baseDir) throws IOException {
        var writer = new CoverageWriter(baseDir);
        var record = new TestCoverageRecord("elder-access-test");

        var policy = new PolicyCoverageData("elder-access-policy", "policy \"test\"\npermit", "policy");
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 2, true);
        policy.recordConditionHit(0, 2, false);
        record.addPolicyCoverage(policy);
        record.recordDecision(Decision.PERMIT);

        writer.write(record);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
