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

import io.sapl.api.coverage.LineCoverageStatus;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.mavenplugin.test.coverage.PathHelper;
import io.sapl.mavenplugin.test.coverage.report.html.WebDependencyFactory.WebDependency;
import lombok.Data;
import lombok.val;
import org.apache.maven.plugin.MojoExecutionException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Generates HTML coverage reports from policy coverage data.
 * <p>
 * Creates a main report page showing coverage ratios and links to individual
 * policy pages. Each policy page displays the source code with line-by-line
 * coverage highlighting using CodeMirror.
 */
public class HtmlLineCoverageReportGenerator {

    /**
     * Generates complete HTML coverage report.
     *
     * @param policies coverage data for all policies
     * @param baseDir output directory for generated files
     * @param policySetHitRatio policy set hit ratio percentage
     * @param policyHitRatio policy hit ratio percentage
     * @param policyConditionHitRatio condition hit ratio percentage
     * @return path to the main report file
     * @throws MojoExecutionException if report generation fails
     */
    public Path generateHtmlReport(Collection<PolicyCoverageData> policies, Path baseDir, float policySetHitRatio,
            float policyHitRatio, float policyConditionHitRatio) throws MojoExecutionException {
        try {
            val policiesWithSource = policies.stream().filter(p -> p.getDocumentSource() != null).toList();

            val mainReportPath = generateMainReport(policiesWithSource, baseDir, policySetHitRatio, policyHitRatio,
                    policyConditionHitRatio);
            generatePolicyReports(policiesWithSource, baseDir);
            copyAssets(baseDir, WebDependencyFactory.getWebDependencies());
            return mainReportPath;
        } catch (IOException e) {
            throw new MojoExecutionException("Error generating HTML coverage report", e);
        }
    }

    private Path generateMainReport(List<PolicyCoverageData> policies, Path baseDir, float policySetHitRatio,
            float policyHitRatio, float policyConditionHitRatio) throws IOException {
        val engine = prepareTemplateEngine();

        val context = new Context();
        context.setVariable("policySetHitRatio", String.format("%.2f", policySetHitRatio));
        context.setVariable("policyHitRatio", String.format("%.2f", policyHitRatio));
        context.setVariable("policyConditionHitRatio", String.format("%.2f", policyConditionHitRatio));
        context.setVariable("documentFileNames", policies.stream().map(PolicyCoverageData::getDocumentName).toList());

        val html       = engine.process("report.html", context);
        val outputFile = baseDir.resolve("html").resolve("report.html");
        createFile(outputFile, html);
        return outputFile;
    }

    private void generatePolicyReports(List<PolicyCoverageData> policies, Path baseDir) throws IOException {
        val engine = prepareTemplateEngine();

        for (val policy : policies) {
            val lineModels = createLineModels(policy);

            val context = new Context();
            context.setVariable("policyTitle", policy.getDocumentName());
            context.setVariable("policyText", policy.getDocumentSource());
            context.setVariable("lineModels", lineModels);

            val html       = engine.process("policy.html", context);
            val outputFile = baseDir.resolve("html").resolve("policies").resolve(policy.getDocumentName() + ".html");
            createFile(outputFile, html);
        }
    }

    private List<HtmlPolicyLineModel> createLineModels(PolicyCoverageData policy) {
        val lineCoverage = policy.getLineCoverage();
        val lines        = policy.getDocumentSource().lines().toList();
        val models       = new ArrayList<HtmlPolicyLineModel>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            val model = new HtmlPolicyLineModel();
            model.setLineContent(lines.get(i));

            if (i < lineCoverage.size()) {
                val coverage = lineCoverage.get(i);
                model.setCssClass(getCssClass(coverage.status()));
                if (coverage.status() == LineCoverageStatus.PARTIALLY_COVERED) {
                    model.setPopoverContent(coverage.getSummary());
                }
            } else {
                model.setCssClass("");
            }
            models.add(model);
        }
        return models;
    }

    private String getCssClass(LineCoverageStatus status) {
        return switch (status) {
        case FULLY_COVERED     -> "coverage-green";
        case PARTIALLY_COVERED -> "coverage-yellow";
        case NOT_COVERED       -> "coverage-red";
        case IRRELEVANT        -> "";
        };
    }

    private void copyAssets(Path baseDir, List<WebDependency> webDependencies) throws IOException {
        for (val webDependency : webDependencies) {
            val sourceRelPathStr = webDependency.sourcePath() + webDependency.fileName();
            val source           = getClass().getClassLoader().getResourceAsStream(sourceRelPathStr);
            if (source == null) {
                throw new IOException("Cannot find asset file: " + sourceRelPathStr);
            }
            val target = baseDir.resolve(webDependency.targetPath()).resolve(webDependency.fileName());
            copyFile(source, target);
        }
    }

    private void createFile(Path filePath, String content) throws IOException {
        PathHelper.createFile(filePath);
        Files.writeString(filePath, content);
    }

    private void copyFile(InputStream source, Path target) throws IOException {
        PathHelper.createParentDirs(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private SpringTemplateEngine prepareTemplateEngine() {
        val engine   = new SpringTemplateEngine();
        val resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/html/templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /**
     * Model for a single line in the HTML policy view.
     */
    @Data
    public static class HtmlPolicyLineModel {
        private String lineContent;
        private String cssClass;
        private String popoverContent;
    }
}
