package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.LinkedList;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.MockingVerification;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class AttributeMockTestPublisher implements AttributeMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE = "You already defined a Mock for %s which is returning specified values";
	private final String fullname;
	
	
	private Sinks.Many<Val> publisher;
	private Flux<Val> returnFlux;

	private final MockRunInformation mockRunInformation;
	private final List<MockingVerification> listMockingVerifications;

	public AttributeMockTestPublisher(String fullname) {
		this.fullname = fullname;
		this.mockRunInformation = new MockRunInformation(fullname);
		this.listMockingVerifications = new LinkedList<>();
		this.listMockingVerifications.add(times(greaterThanOrEqualTo(1)));
		
		this.publisher = Sinks.many().replay().latest();
		this.returnFlux = this.publisher.asFlux();

	}

	public void mockEmit(Val returns) {
		this.publisher.tryEmitNext(returns);
	}

	@Override
	public Flux<Val> evaluate() {
		this.mockRunInformation.saveCall(new FunctionCallSimple());
		return this.returnFlux;
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
