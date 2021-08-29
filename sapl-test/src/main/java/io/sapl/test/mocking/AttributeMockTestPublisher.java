package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;

import java.util.LinkedList;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.MockingVerification;
import reactor.core.publisher.Flux;
import reactor.test.publisher.TestPublisher;

public class AttributeMockTestPublisher implements AttributeMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE = "You already defined a Mock for %s which is returning specified values";
	private final String fullname;
	
	
	private TestPublisher<Val> testpublisher;

	private final MockRunInformation mockRunInformation;
	private final List<MockingVerification> listMockingVerifications;

	public AttributeMockTestPublisher(String fullname) {
		this.testpublisher = null;
		this.fullname = fullname;

		this.mockRunInformation = new MockRunInformation(fullname);
		this.listMockingVerifications = new LinkedList<>();
	}

	public void markMock() {
		this.testpublisher = TestPublisher.<Val>create();

		this.listMockingVerifications.add(times(1));
	}

	public void mockEmit(Val returns) {
		if (this.testpublisher == null) {
			throw new SaplTestException("Undefined internal state. Please report a bug to the library authors!");
		}
		this.testpublisher.next(returns);
	}

	@Override
	public Flux<Val> evaluate() {
		// TODO check if call parameter for attributes should stay irrelevant
		this.mockRunInformation.saveCall(new FunctionCallSimple());

		if (this.testpublisher == null) {
			throw new SaplTestException("Undefined internal state. Please report a bug to the library authors!");
		}

		return this.testpublisher.flux();
	}

	@Override
	public void assertVerifications() {
		this.listMockingVerifications.stream().forEach((verification) -> verification.verify(this.mockRunInformation));
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE, this.fullname);
	}
}
