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
package io.sapl.mavenplugin.test.coverage.report.html;

import io.sapl.mavenplugin.test.coverage.PathHelper;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.report.html.WebDependencyFactory.WebDependency;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import lombok.Data;
import org.apache.maven.plugin.MojoExecutionException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HtmlLineCoverageReportGenerator {

    public Path generateHtmlReport(Collection<SaplDocumentCoverageInformation> documents, Path baseDir,
            float policySetHitRatio, float policyHitRatio, float policyConditionHitRatio)
            throws MojoExecutionException {
        Path pathToReportsMainSite;
        try {
            pathToReportsMainSite = generateSAPLCoverageReport(policySetHitRatio, policyHitRatio,
                    policyConditionHitRatio, documents, baseDir);
            generateSAPLPolicyReports(documents, baseDir);
            copyAssets(baseDir, WebDependencyFactory.getWebDependencies());
        } catch (IOException e) {
            throw new MojoExecutionException("Error while using the filesystem", e);
        }
        return pathToReportsMainSite;
    }

    private void generateSAPLPolicyReports(Collection<SaplDocumentCoverageInformation> documents, Path basedir)
            throws IOException {
        SpringTemplateEngine springTemplateEngine = prepareTemplateEngine();

        for (SaplDocumentCoverageInformation doc : documents) {
            List<String>              lines  = readPolicyDocument(doc.getPathToDocument());
            List<HtmlPolicyLineModel> models = createHtmlPolicyLineModel(lines, doc);

            // prepare context
            Context context = new Context();
            context.setVariable("policyTitle", doc.getPathToDocument().getFileName());
            context.setVariable("policyText", String.join("\n", lines));
            context.setVariable("lineModels", models);

            // process the template and context
            String htmlFileAsString = springTemplateEngine.process("policy.html", context);

            // write the file
            Path outputFile = basedir.resolve("html").resolve("policies")
                    .resolve(doc.getPathToDocument().getFileName() + ".html");
            createFile(outputFile, htmlFileAsString);
        }

    }

    private Path generateSAPLCoverageReport(float policySetHitRatio, float policyHitRatio,
            float policyConditionHitRatio, Collection<SaplDocumentCoverageInformation> documents, Path basedir)
            throws IOException {
        SpringTemplateEngine springTemplateEngine = prepareTemplateEngine();

        // prepare context
        Context context = new Context();
        context.setVariable("policySetHitRatio", policySetHitRatio);
        context.setVariable("policyHitRatio", policyHitRatio);
        context.setVariable("policyConditionHitRatio", policyConditionHitRatio);
        context.setVariable("documentFileNames",
                documents.stream().map(doc -> doc.getPathToDocument().getFileName()).toList());

        // process the template and context
        String htmlFileAsString = springTemplateEngine.process("report.html", context);

        // write the file
        Path outputFile = basedir.resolve("html").resolve("report.html");
        createFile(outputFile, htmlFileAsString);

        return outputFile;
    }

    private List<HtmlPolicyLineModel> createHtmlPolicyLineModel(List<String> lines,
            SaplDocumentCoverageInformation document) {
        List<HtmlPolicyLineModel> models = new LinkedList<>();

        for (int i = 0; i < lines.size(); i++) {
            final var model = new HtmlPolicyLineModel();
            model.setLineContent(lines.get(i));
            final var line         = document.getLine(i + 1);
            final var coveredValue = line.getCoveredValue();
            assertValidCoveredValue(coveredValue);
            switch (coveredValue) {
            case FULLY  -> model.setCssClass("coverage-green");
            case NEVER  -> model.setCssClass("coverage-red");
            case PARTLY -> {
                model.setCssClass("coverage-yellow");
                model.setPopoverContent(String.format("%d of %d branches covered", line.getCoveredBranches(),
                        line.getBranchesToCover()));
            }
            default     -> model.setCssClass("");
            }
            models.add(model);
        }
        return models;
    }

    private void copyAssets(Path basedir, List<WebDependency> webDependencies) throws IOException {
        for (var webDependency : webDependencies) {
            String            sourceRelPathStr = webDependency.sourcePath() + webDependency.fileName();
            final InputStream source           = getClass().getClassLoader().getResourceAsStream(sourceRelPathStr);
            if (source == null) {
                final String msg = String.format("Cannot find file: %s while copying assets.", sourceRelPathStr);
                throw new IOException(msg);
            }
            final Path target = basedir.resolve(webDependency.targetPath()).resolve(webDependency.fileName());
            copyFile(source, target);
        }
    }

    private List<String> readPolicyDocument(Path filePath) throws IOException {
        return Files.readAllLines(filePath);
    }

    private void createFile(Path filePath, String content) throws IOException {
        PathHelper.createFile(filePath);
        Files.writeString(filePath, content);
    }

    private void copyFile(InputStream source, Path target) throws IOException {
        PathHelper.createParentDirs(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void assertValidCoveredValue(LineCoveredValue coveredValue) {
        if (coveredValue == LineCoveredValue.FULLY || coveredValue == LineCoveredValue.PARTLY
                || coveredValue == LineCoveredValue.NEVER || coveredValue == LineCoveredValue.IRRELEVANT)
            return;
        throw new SaplTestException("Unexpected enum value: " + coveredValue);
    }

    private SpringTemplateEngine prepareTemplateEngine() {
        SpringTemplateEngine        springTemplateEngine        = new SpringTemplateEngine();
        ClassLoaderTemplateResolver classLoaderTemplateResolver = new ClassLoaderTemplateResolver();
        classLoaderTemplateResolver.setPrefix("/html/templates/");
        classLoaderTemplateResolver.setSuffix(".html");
        classLoaderTemplateResolver.setCharacterEncoding("UTF-8");
        springTemplateEngine.setTemplateResolver(classLoaderTemplateResolver);
        return springTemplateEngine;
    }

    @Data
    static class HtmlPolicyLineModel {
        String lineContent;
        String cssClass;
        String popoverContent;
    }
}
