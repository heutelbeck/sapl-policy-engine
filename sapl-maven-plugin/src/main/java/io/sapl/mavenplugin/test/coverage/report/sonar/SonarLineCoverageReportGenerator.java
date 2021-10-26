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
package io.sapl.mavenplugin.test.coverage.report.sonar;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collection;

import io.sapl.mavenplugin.test.coverage.PathHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentLineCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.Coverage;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.ObjectFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.maven.plugin.logging.Log;

public class SonarLineCoverageReportGenerator {
	private ObjectFactory FACTORY = new ObjectFactory();

	public void generateSonarLineCoverageReport(Collection<SaplDocumentCoverageInformation> documents, Log log,
			Path basedir, String policyPath, File mavenBaseDir) {
		Coverage sonarCoverage = FACTORY.createCoverage();
		sonarCoverage.setVersion(BigInteger.valueOf(1));
		for (var doc : documents) {
			addFile(sonarCoverage, doc, mavenBaseDir, policyPath);
		}
		JAXBContext context;
		try {
			context = JAXBContext.newInstance(Coverage.class, ObjectFactory.class);
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			Path filePath = basedir.resolve("sonar").resolve("sonar-generic-coverage.xml");
			if (!filePath.toFile().exists()) {
				PathHelper.createFile(filePath);
			}
			marshaller.marshal(sonarCoverage, filePath.toFile());
		} catch (JAXBException e) {
			log.error("Error unmarshalling Coverage information to Sonarqube generic coverage format", e);
		} catch (IOException e) {
			log.error("Error writing file", e);
		}
	}

	private void addFile(Coverage coverage, SaplDocumentCoverageInformation doc, File mavenBaseDir, String policyPath) {
		Coverage.File sonarFile = FACTORY.createCoverageFile();

		/**
		 * Sonarqube seems to require a path to the sapl file in the src directory
		 * 
		 * The path on the classpath "target/test-classes/policies/policySimple.sapl is
		 * getting ignored because unknown to sonarqube
		 */
		sonarFile.setPath(mavenBaseDir.toPath().resolve("src").resolve("main").resolve("resources").resolve(policyPath)
				.resolve(doc.getPathToDocument().getFileName()).toString());

		for (int i = 1; i <= doc.getLineCount(); i++) {
			addLine(sonarFile, doc.getLine(i));
		}
		coverage.getFile().add(sonarFile);
	}

	private void addLine(Coverage.File file, SaplDocumentLineCoverageInformation line) {
		if (line.getCoveredValue() == LineCoveredValue.IRRELEVANT) {
			return;
		}
		Coverage.File.LineToCover sonarLine = FACTORY.createCoverageFileLineToCover();
		sonarLine.setLineNumber(BigInteger.valueOf(line.getLineNumber()));
		sonarLine.setCovered(line.getCoveredValue() == LineCoveredValue.NEVER ? false : true);
		if (line.getCoveredValue() == LineCoveredValue.PARTLY) {
			sonarLine.setBranchesToCover(BigInteger.valueOf(line.getBranchesToCover()));
			sonarLine.setCoveredBranches(BigInteger.valueOf(line.getCoveredBranches()));
		}
		file.getLineToCover().add(sonarLine);
	}

}
