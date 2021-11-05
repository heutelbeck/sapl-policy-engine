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
package io.sapl.test.unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixtureTemplate;
import io.sapl.test.coverage.api.CoverageAPIFactory;
import io.sapl.test.lang.TestSaplInterpreter;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;
import io.sapl.test.utils.ClasspathHelper;
import reactor.core.Exceptions;

public class SaplUnitTestFixture extends SaplTestFixtureTemplate {

	private static final String ERROR_MESSAGE_MISSING_SAPL_DOCUMENT_NAME = "Bevor constructing a test case you have to specifiy the filename where to find your SAPL policy!"
			+ "\n\nProbably you forgot to call \".setSaplDocumentName(\"\")\"";

	private final String saplDocumentName;

	/**
	 * Fixture for constructing a unit test case
	 * @param saplDocumentName path relativ to your classpath to the sapl document. If
	 * your policies are located at the root of the classpath or in the standard path
	 * "policies/" in your resources folder you only have to specifiy the name of the
	 * .sapl file. If your policies are located at some special place you have to
	 * configure a relativ path like "yourspecialdir/policies/mypolicy.sapl"
	 */
	public SaplUnitTestFixture(String saplDocumentName) {
		this.saplDocumentName = saplDocumentName;
	}

	@Override
	public GivenStep constructTestCaseWithMocks() {
		if (this.saplDocumentName == null || this.saplDocumentName.isEmpty()) {
			throw new SaplTestException(ERROR_MESSAGE_MISSING_SAPL_DOCUMENT_NAME);
		}
		return StepBuilder.newBuilderAtGivenStep(readSaplDocument(), this.attributeCtx, this.functionCtx,
				this.variables);
	}

	@Override
	public WhenStep constructTestCase() {
		if (this.saplDocumentName == null || this.saplDocumentName.isEmpty()) {
			throw new SaplTestException(ERROR_MESSAGE_MISSING_SAPL_DOCUMENT_NAME);
		}
		return StepBuilder.newBuilderAtWhenStep(readSaplDocument(), this.attributeCtx, this.functionCtx,
				this.variables);
	}

	private SAPL readSaplDocument() {
		String filename = constructFileEnding(this.saplDocumentName);

		SAPLInterpreter interpreter = new TestSaplInterpreter(
				CoverageAPIFactory.constructCoverageHitRecorder(resolveCoverageBaseDir()));

		return interpreter.parse(findFileOnClasspath(filename));
	}

	private String constructFileEnding(String filename) {
		if (this.saplDocumentName.endsWith(".sapl")) {
			return filename;
		}
		else {
			return filename + ".sapl";
		}
	}

	private String findFileOnClasspath(String filename) {
		Path path = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), filename);
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw Exceptions.propagate(e);
		}
	}

}
