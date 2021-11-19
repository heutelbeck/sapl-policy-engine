/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static j2html.TagCreator.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sapl.mavenplugin.test.coverage.PathHelper;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import j2html.attributes.Attribute;
import j2html.tags.ContainerTag;
import j2html.tags.EmptyTag;
import lombok.Data;

public class HtmlLineCoverageReportGenerator {

	public Path generateHtmlReport(Collection<SaplDocumentCoverageInformation> documents, Log log, Path basedir,
			float policySetHitRatio, float policyHitRatio, float policyConditionHitRatio)
			throws MojoExecutionException {
		Path index;
		try {
			index = generateMainSite(policySetHitRatio, policyHitRatio, policyConditionHitRatio, documents, basedir);

			generateCustomCSS(basedir);

			copyAssets(basedir);

			for (var doc : documents) {
				generatePolicySite(doc, basedir);
			}
		}
		catch (IOException e) {
			throw new MojoExecutionException("Error while using the filesystem", e);
		}
		return index;

	}

	private Path generateMainSite(float policySetHitRatio, float policyHitRatio, float policyConditionHitRatio,
			Collection<SaplDocumentCoverageInformation> documents, Path basedir) throws IOException {

		// @formatter:off
		ContainerTag mainSite =
			html(
				head(
					meta().withCharset("utf-8"),
					meta().withName("viewport").withContent("width=device-width, initial-scale=1, shrink-to-fit=no"),
					getBootstrapCss(),
					link().withRel("stylesheet").withHref("assets/main.css"),
					link().withRel("icon").withHref("assets/favicon.png").withType("image/png"),
					title("SAPL Coverage Report")),
				body(
					main(
						attrs("#main.content"),
						nav(
							a(
								img()
									.withSrc("assets/logo-header.png")
									.withStyle("display: inline-block; height: 60px; margin-right: 10px")
							)
								.withClass("navbar-brand")
								.withHref("#")
						)
							.withClass("navbar navbar-light")
							.withStyle("background-color: #20232a"),
						h1("SAPL Coverage Report").withStyle("padding: 1.25rem;"),

						div(
							div("SAPL Coverage Ratio").withClass("card-header"),
							div(
								p("PolicySet Hit Ratio: " + policySetHitRatio + "%"),
								p("Policy Hit Ratio: " + policyHitRatio + "%"),
								p("PolicyCondition Hit Ratio: " + policyConditionHitRatio + "%")
							).withClass("card-body")
						).withClass("card").withStyle("padding: 1.25rem"),

						div(
							div("Show coverage per SAPL document").withClass("card-header"),
							div(
								div(
									each(
										documents,
										document -> {
											var filename = document.getPathToDocument().getFileName() != null ? document.getPathToDocument().getFileName().toString() : "";
											return a(filename)
													.withHref("policies/" + filename + ".html")
													.withClass("list-group-item list-group-item-action");
										}
									)
								).withClass("list-group")
							).withClass("card-body")
						).withClass("card").withStyle("padding: 1.25rem")
					),
					getJquery(),
					getPopper(),
					getBootstrapJs()
				)
			);
		// @formatter:on

		Path filePath = basedir.resolve("html").resolve("index.html");
		createFile(filePath, mainSite.render());
		return filePath;
	}

	private void generateCustomCSS(Path basedir) throws IOException {
		String css = ".coverage-green span { background-color: #ccffcc; }\n"
				+ ".coverage-yellow span { background-color: #ffffcc; }\n"
				+ ".coverage-red span { background-color: #ffaaaa; }\n"
				+ ".CodeMirror { height: calc(100% - 50px) !important; }\n";
		Path cssPath = basedir.resolve("html").resolve("assets").resolve("main.css");
		createFile(cssPath, css);
	}

	private void generatePolicySite(SaplDocumentCoverageInformation document, Path basedir) throws IOException {

		List<String> lines = readPolicyDocument(document.getPathToDocument());

		List<HtmlPolicyLineModel> models = createHtmlPolicyLineModel(lines, document);

		Path filename = document.getPathToDocument().getFileName();
		ContainerTag policySite = createPolicySite_CodeMirror(filename != null ? filename.toString() : "", models);

		Path policyPath = basedir.resolve("html").resolve("policies").resolve(filename + ".html");
		createFile(policyPath, policySite.renderFormatted());
	}

	private ContainerTag createPolicySite_CodeMirror(String filename, List<HtmlPolicyLineModel> models)
			throws IOException {

		StringBuilder wholeTextOfPolicy = new StringBuilder();
		StringBuilder htmlReportCodeMirrorJSLineClassStatements = new StringBuilder("\n");
		for (int i = 0; i < models.size(); i++) {
			var model = models.get(i);
			wholeTextOfPolicy.append(model.getLineContent()).append('\n');
			htmlReportCodeMirrorJSLineClassStatements
					.append(String.format("editor.addLineClass(%s, \"text\", \"%s\");%n", i, model.getCssClass()));
			if (model.getPopoverContent() != null) {
				htmlReportCodeMirrorJSLineClassStatements.append(String.format(
						"editor.getDoc().markText({line:%s,ch:0},{line:%s,ch:%d},{attributes: { \"data-toggle\": \"popover\", \"data-trigger\": \"hover\", \"data-placement\": \"top\", \"data-content\": \"%s\" }})%n",
						i, i, model.getLineContent().toCharArray().length, model.getPopoverContent()));
			}
		}

		String htmlReportCodeMirrorJsTemplate = readFileFromClasspath("scripts/html-report-codemirror-template.js");
		String htmlReportCoreMirrorJS = htmlReportCodeMirrorJsTemplate.replace("{{replacement}}",
				Stream.of(htmlReportCodeMirrorJSLineClassStatements).collect(Collectors.joining()));

		// @formatter:off
		return html(
			    head(
			        title("SAPL Coverage Report"),
			    	meta().withCharset("utf-8"),
			    	meta().withName("viewport").withContent("width=device-width, initial-scale=1, shrink-to-fit=no"),
					getBootstrapCss(),
			        link().withRel("stylesheet").withHref("../assets/main.css"),
			        link().withRel("stylesheet").withHref("../assets/codemirror.css"),
			        script().withSrc("../assets/require.js")
			        ),
			    body(
			        main(attrs("#main.content"),
	        			nav(
        					ol(
    							li(
									a("Home").withHref("../index.html")
								).withClass("breadcrumb-item"),
    							li(
									filename
								).withClass("breadcrumb-item active").attr(new Attribute("aria-current", "page"))
							).withClass("breadcrumb")
    					).attr(new Attribute("aria-label", "breadcrumb")),
			            div(
		            		h1(filename).withStyle("margin-bottom: 2vw"),
		            		textarea(wholeTextOfPolicy.toString()).withId("policyTextArea")
	            		).withClass("card-body").withStyle("height: 80%")
			        ).withStyle("height: 100vh"),
	        		script().with(rawHtml(htmlReportCoreMirrorJS))
			    )
			);
		// @formatter:on

	}

	private List<HtmlPolicyLineModel> createHtmlPolicyLineModel(List<String> lines,
			SaplDocumentCoverageInformation document) {
		List<HtmlPolicyLineModel> models = new LinkedList<>();

		for (int i = 0; i < lines.size(); i++) {
			var model = new HtmlPolicyLineModel();
			model.setLineContent(lines.get(i));
			var line = document.getLine(i + 1);
			var coveredValue = line.getCoveredValue();
			assertValidCoveredValue(coveredValue);
			switch (coveredValue) {
			case FULLY:
				model.setCssClass("coverage-green");
				break;
			case NEVER:
				model.setCssClass("coverage-red");
				break;
			case PARTLY:
				model.setCssClass("coverage-yellow");
				model.setPopoverContent(String.format("%d of %d branches covered", line.getCoveredBranches(),
						line.getBranchesToCover()));
				break;
			case IRRELEVANT:
			default:
				model.setCssClass("");
				break;
			}
			models.add(model);
		}
		return models;
	}

	private void copyAssets(Path basedir) throws IOException {
		Path logoHeaderPath = basedir.resolve("html").resolve("assets").resolve("logo-header.png");
		var logoSourcePath = getClass().getClassLoader().getResourceAsStream("images/logo-header.png");
		copyFile(logoSourcePath, logoHeaderPath);

		Path faviconPath = basedir.resolve("html").resolve("assets").resolve("favicon.png");
		var faviconSourcePath = getClass().getClassLoader().getResourceAsStream("images/favicon.png");
		copyFile(faviconSourcePath, faviconPath);

		Path requireJSTargetPath = basedir.resolve("html").resolve("assets").resolve("require.js");
		var requireJS = getClass().getClassLoader().getResourceAsStream("scripts/require.js");
		copyFile(requireJS, requireJSTargetPath);

		Path saplCodeMirrorModeJSTargetPath = basedir.resolve("html").resolve("assets").resolve("sapl-mode.js");
		var saplCodeMirrorModeJS = getClass().getClassLoader().getResourceAsStream("dependency-resources/sapl-mode.js");
		copyFile(saplCodeMirrorModeJS, saplCodeMirrorModeJSTargetPath);

		Path saplCodeMirrorSimpleAddonTargetPath = basedir.resolve("html").resolve("assets").resolve("codemirror")
				.resolve("addon").resolve("mode").resolve("simple.js");
		var saplCodeMirrorSimpleAddon = getClass().getClassLoader().getResourceAsStream("scripts/simple.js");
		copyFile(saplCodeMirrorSimpleAddon, saplCodeMirrorSimpleAddonTargetPath);

		Path saplCodeMirrorJsTargetPath = basedir.resolve("html").resolve("assets").resolve("codemirror").resolve("lib")
				.resolve("codemirror.js");
		var saplCodeMirrorJs = getClass().getClassLoader().getResourceAsStream("scripts/codemirror.js");
		copyFile(saplCodeMirrorJs, saplCodeMirrorJsTargetPath);

		Path saplCodeMirrorCssTargetPath = basedir.resolve("html").resolve("assets").resolve("codemirror.css");
		var saplCodeMirrorCss = getClass().getClassLoader().getResourceAsStream("scripts/codemirror.css");
		copyFile(saplCodeMirrorCss, saplCodeMirrorCssTargetPath);
	}

	private String readFileFromClasspath(String filename) throws IOException {
		var stream = getClass().getClassLoader().getResourceAsStream((filename));
		String fileContent = "";
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			fileContent = reader.lines().collect(Collectors.joining("\n"));
		}
		return fileContent;
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

	private ContainerTag getJquery() {
		return script().withSrc("https://code.jquery.com/jquery-3.2.1.slim.min.js")
				.attr(new Attribute("integrity",
						"sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN"))
				.attr(new Attribute("crossorigin", "anonymous"));
	}

	private ContainerTag getPopper() {
		return script().withSrc("https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js")
				.attr(new Attribute("integrity",
						"sha384-ApNbgh9B+Y1QKtv3Rn7W3mgPxhU9K/ScQsAP7hUibX39j7fakFPskvXusvfa0b4Q"))
				.attr(new Attribute("crossorigin", "anonymous"));
	}

	private ContainerTag getBootstrapJs() {
		return script().withSrc("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js")
				.attr(new Attribute("integrity",
						"sha384-JZR6Spejh4U02d8jOt6vLEHfe/JQGiRRSQQxSfFWpi1MquVdAyjUar5+76PVCmYl"))
				.attr(new Attribute("crossorigin", "anonymous"));
	}

	private EmptyTag getBootstrapCss() {
		return link().withRel("stylesheet")
				.withHref("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css")
				.attr(new Attribute("integrity",
						"sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"))
				.attr(new Attribute("crossorigin", "anonymous"));
	}

	private void assertValidCoveredValue(LineCoveredValue coveredValue) {
		if (coveredValue == LineCoveredValue.FULLY || coveredValue == LineCoveredValue.PARTLY
				|| coveredValue == LineCoveredValue.NEVER || coveredValue == LineCoveredValue.IRRELEVANT)
			return;
		throw new SaplTestException("Unexpected enum value: " + coveredValue);
	}

	@Data
	static class HtmlPolicyLineModel {

		String lineContent;

		String cssClass;

		String popoverContent;

	}

}
