/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;

@Named
@Singleton
public class SaplDocumentReader {

    private static final String ERROR_PATH_NOT_FOUND = "Error reading coverage targets: Unable to find the directory \"%s\" on the classpath";

    private static final String ERROR_PATH_WAS_FILE = "Error reading coverage targets: Unable to find policies at \"%s\" on the classpath. The path points to a file.";

    public Collection<SaplDocument> retrievePolicyDocuments(Log log, MavenProject project, String policyPath)
            throws MojoExecutionException {
        DefaultSAPLInterpreter interpreter = new DefaultSAPLInterpreter();

        File policyDir = findPolicyDirOnClasspath(project, policyPath);

        log.debug("Looking for policies in directory \"" + policyDir.getAbsolutePath() + "\"");

        File[] files = policyDir.listFiles();
        if (files == null) {
            throw new MojoExecutionException(String.format(ERROR_PATH_WAS_FILE, policyDir.getAbsolutePath()));
        }

        List<SaplDocument> saplDocuments = new LinkedList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".sapl")) {
                String fileContent;
                try {
                    log.debug(String.format("Loading coverage target from file \"%s\"", file.getPath()));
                    fileContent = Files.readString(file.toPath());
                    int lineCount = Files.readAllLines(file.toPath()).size();
                    saplDocuments.add(new SaplDocument(file.toPath(), lineCount, interpreter.parse(fileContent)));
                } catch (IOException e) {
                    log.error("Error reading file " + file, e);
                    throw new MojoExecutionException("Error reading file", e);
                }
            }
        }
        return saplDocuments;
    }

    private File findPolicyDirOnClasspath(MavenProject project, String policyPath) throws MojoExecutionException {

        List<String>  projectTestClassPathElements;
        StringBuilder builder = new StringBuilder(String.format(ERROR_PATH_NOT_FOUND, policyPath));
        File          result  = null;
        try {
            projectTestClassPathElements = project.getRuntimeClasspathElements();
            builder.append(" - We looked at: ").append(System.lineSeparator());

            for (String element : projectTestClassPathElements) {
                Path path = Path.of(element).resolve(policyPath);
                builder.append("* ").append(path).append(System.lineSeparator());
                File dir = path.toFile();
                if (dir.exists()) {
                    result = dir;
                    break;
                }
            }
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot get RuntimeClasspathElements from the current maven project", e);
        }

        if (result != null) {
            return result;
        } else {
            throw new MojoExecutionException("Error reading coverage targets: " + builder);
        }
    }

}
