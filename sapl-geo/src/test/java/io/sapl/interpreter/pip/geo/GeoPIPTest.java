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
package io.sapl.interpreter.pip.geo;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.GeoPolicyInformationPoint;
import reactor.test.StepVerifier;

class GeoPIPTest {

	private static final GeoPolicyInformationPoint AF = new GeoPolicyInformationPoint();

	@Test
	void postgisTest() {
		StepVerifier.create(AF.traccar(Val.of(TraccarConnection.AF_TEST), null).take(1))
				.expectNextMatches(v -> TraccarConnection.TEST_OKAY.equals(v.getText())).verifyComplete();
	}

	@Test
	void traccarTest() {
		StepVerifier.create(AF.postgis(Val.of(PostGISConnection.AF_TEST), null).take(1))
				.expectNextMatches(v -> PostGISConnection.TEST_OKAY.equals(v.getText())).verifyComplete();
	}

	@Test
	void kmlTest() {
		StepVerifier.create(AF.kml(Val.of(""), null).take(1))
				.expectNextMatches(v -> KMLImport.TEST_OKAY.equals(v.getText())).verifyComplete();
	}

}
