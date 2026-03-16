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
package io.sapl.test.coverage.report.html;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.sapl.api.coverage.LineCoverageStatus;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.test.coverage.report.html.WebDependencyFactory.WebDependency;
import lombok.val;

/**
 * Generates HTML coverage reports from policy coverage data.
 * <p>
 * Creates a main report page showing coverage ratios and links to individual
 * policy pages. Each policy page displays the source code with line-by-line
 * coverage highlighting using CodeMirror.
 */
public class HtmlLineCoverageReportGenerator {

    private static final String ERROR_ASSET_FILE_NOT_FOUND    = "Cannot find asset file: %s";
    private static final String ERROR_TEMPLATE_FILE_NOT_FOUND = "Cannot find template file: %s";

    private static final String TEMPLATE_PREFIX = "/io/sapl/test/coverage/report/html/templates/";

    /**
     * Generates complete HTML coverage report.
     *
     * @param policies coverage data for all policies
     * @param baseDir output directory for generated files
     * @param policySetHitRatio policy set hit ratio percentage
     * @param policyHitRatio policy hit ratio percentage
     * @param policyConditionHitRatio condition hit ratio percentage
     * @return path to the main report file
     * @throws IOException if report generation fails
     */
    public Path generateHtmlReport(Collection<PolicyCoverageData> policies, Path baseDir, float policySetHitRatio,
            float policyHitRatio, float policyConditionHitRatio) throws IOException {
        val policiesWithSource = policies.stream().filter(p -> p.getDocumentSource() != null).toList();

        val mainReportPath = generateMainReport(policiesWithSource, baseDir, policySetHitRatio, policyHitRatio,
                policyConditionHitRatio);
        generatePolicyReports(policiesWithSource, baseDir);
        copyAssets(baseDir, WebDependencyFactory.getWebDependencies());
        return mainReportPath;
    }

    private Path generateMainReport(List<PolicyCoverageData> policies, Path baseDir, float policySetHitRatio,
            float policyHitRatio, float policyConditionHitRatio) throws IOException {
        val template = loadTemplate("report.html");

        val links = new StringBuilder();
        for (val policy : policies) {
            val name = policy.getDocumentName();
            links.append("\t\t\t\t\t<a href=\"policies/").append(escapeHtml(name))
                    .append(".html\" class=\"list-group-item list-group-item-action\">").append(escapeHtml(name))
                    .append("</a>\n");
        }

        val html = template.replace("{{policySetHitRatio}}", String.format("%.2f", policySetHitRatio))
                .replace("{{policyHitRatio}}", String.format("%.2f", policyHitRatio))
                .replace("{{policyConditionHitRatio}}", String.format("%.2f", policyConditionHitRatio))
                .replace("{{documentLinks}}", links.toString());

        val outputFile = baseDir.resolve("html").resolve("report.html");
        writeFile(outputFile, html);
        return outputFile;
    }

    private void generatePolicyReports(List<PolicyCoverageData> policies, Path baseDir) throws IOException {
        val template = loadTemplate("policy.html");

        for (val policy : policies) {
            val lineModels     = createLineModels(policy);
            val lineModelsJson = toJson(lineModels);

            val html = template.replace("{{policyTitle}}", escapeHtml(policy.getDocumentName()))
                    .replace("{{policyText}}", escapeHtml(policy.getDocumentSource()))
                    .replace("{{lineModelsJson}}", lineModelsJson);

            val outputFile = baseDir.resolve("html").resolve("policies").resolve(policy.getDocumentName() + ".html");
            writeFile(outputFile, html);
        }
    }

    private List<HtmlPolicyLineModel> createLineModels(PolicyCoverageData policy) {
        val lineCoverage = policy.getLineCoverage();
        val lines        = policy.getDocumentSource().lines().toList();
        val models       = new ArrayList<HtmlPolicyLineModel>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            String cssClass       = "";
            String popoverContent = null;

            if (i < lineCoverage.size()) {
                val coverage = lineCoverage.get(i);
                cssClass = getCssClass(coverage.status());
                if (coverage.status() == LineCoverageStatus.PARTIALLY_COVERED) {
                    popoverContent = coverage.getSummary();
                }
            }
            models.add(new HtmlPolicyLineModel(lines.get(i), cssClass, popoverContent));
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

    private String toJson(List<HtmlPolicyLineModel> models) {
        val sb = new StringBuilder("[");
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            val model = models.get(i);
            sb.append('{');
            if (!model.cssClass().isEmpty()) {
                sb.append("\"c\":\"").append(escapeJson(model.cssClass())).append('"');
                if (model.popoverContent() != null) {
                    sb.append(',');
                }
            }
            if (model.popoverContent() != null) {
                sb.append("\"p\":\"").append(escapeJson(model.popoverContent())).append('"');
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private void copyAssets(Path baseDir, List<WebDependency> webDependencies) throws IOException {
        for (val webDependency : webDependencies) {
            val sourceRelPathStr = webDependency.sourcePath() + webDependency.fileName();
            val source           = getClass().getClassLoader().getResourceAsStream(sourceRelPathStr);
            if (source == null) {
                throw new IOException(ERROR_ASSET_FILE_NOT_FOUND.formatted(sourceRelPathStr));
            }
            val target = baseDir.resolve(webDependency.targetPath()).resolve(webDependency.fileName());
            copyFile(source, target);
        }
    }

    private String loadTemplate(String name) throws IOException {
        val path = TEMPLATE_PREFIX + name;
        try (val stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException(ERROR_TEMPLATE_FILE_NOT_FOUND.formatted(path));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        ensureParentDirectory(filePath);
        Files.writeString(filePath, content);
    }

    private static void copyFile(InputStream source, Path target) throws IOException {
        ensureParentDirectory(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void ensureParentDirectory(Path path) throws IOException {
        val parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

}
