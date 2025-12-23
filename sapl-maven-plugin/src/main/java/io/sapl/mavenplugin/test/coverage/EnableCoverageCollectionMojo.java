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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

@Mojo(name = "enable-coverage-collection", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES)
public class EnableCoverageCollectionMojo extends AbstractMojo {

    @Parameter(defaultValue = "true")
    private boolean coverageEnabled;

    @Parameter
    private String outputDir;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Override
    public void execute() throws MojoExecutionException {
        if (this.coverageEnabled) {
            try {
                FileUtils.deleteDirectory(PathHelper.resolveBaseDir(outputDir, projectBuildDir, getLog()).toFile());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to delete directory", e);
            }
        }
    }

}
