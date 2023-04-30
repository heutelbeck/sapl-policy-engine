/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.mocking.attribute;

import static io.sapl.test.Imports.times;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.MockingVerification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class AttributeMockPublisher implements AttributeMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE = "You already defined a Mock for %s which is returning specified values";

	private final String fullName;

	private final Sinks.Many<Val> publisher;

	private final Flux<Val> returnFlux;

	private final MockRunInformation mockRunInformation;

	private final List<MockingVerification> listMockingVerifications;

	public AttributeMockPublisher(String fullName) {
		this.fullName = fullName;
		this.mockRunInformation = new MockRunInformation(fullName);
		this.listMockingVerifications = new LinkedList<>();
		this.listMockingVerifications.add(times(greaterThanOrEqualTo(1)));

		this.publisher = Sinks.many().replay().latest();
		this.returnFlux = this.publisher.asFlux();

	}

	public void mockEmit(Val returns) {
		this.publisher.tryEmitNext(returns);
	}

	@Override
	public Flux<Val> evaluate(String attributeName, Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args) {
		this.mockRunInformation.saveCall(new MockCall());
		return this.returnFlux.map(val->val.withTrace(AttributeMockPublisher.class,Map.of("attributeName",Val.of(attributeName))));
	}

	@Override
	public void assertVerifications() {
		this.listMockingVerifications.forEach((verification) -> verification.verify(this.mockRunInformation));
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE, this.fullName);
	}

}
