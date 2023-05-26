/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage.report.sonar.model;

import jakarta.xml.bind.annotation.XmlRegistry;

/**
 * This object contains factory methods for each Java content interface and Java element
 * interface generated in the {@code io.sapl.test.mavenplugin.model.sonar} package.
 * <p>
 * An ObjectFactory allows you to programmatically construct new instances of the Java
 * representation for XML content. The Java representation of XML content can consist of
 * schema derived interfaces and classes representing the binding of schema type
 * definitions, element declarations and model groups. Factory methods for each of these
 * are provided in this class.
 *
 */
@XmlRegistry
public class ObjectFactory {

	/**
	 * Create a new ObjectFactory that can be used to create new instances of schema
	 * derived classes for package: {@code io.sapl.test.mavenplugin.model.sonar}
	 *
	 */
	public ObjectFactory() {
	}

	/**
	 * Create an instance of {@link Coverage }
	 * @return the coverage
	 *
	 */
	public Coverage createCoverage() {
		return new Coverage();
	}

	/**
	 * Create an instance of {@link Coverage.File }
	 * @return the coverage file
	 *
	 */
	public Coverage.File createCoverageFile() {
		return new Coverage.File();
	}

	/**
	 * Create an instance of {@link Coverage.File.LineToCover }
	 * @return the lineToConverter
	 *
	 */
	public Coverage.File.LineToCover createCoverageFileLineToCover() {
		return new Coverage.File.LineToCover();
	}

}