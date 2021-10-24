package io.sapl.test.mocking.attribute;

import static io.sapl.test.Imports.times;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.MockingVerification;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;

public class AttributeMockTiming implements AttributeMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_TIMING_MODE = "You already defined a Mock for %s which is returning specified values with a timing";
	private final String fullname;
	
	private Val[] returnValues;
	private Duration timing;

	private final MockRunInformation mockRunInformation;
	private final List<MockingVerification> listMockingVerifications;

	public AttributeMockTiming(String fullname) {
		this.fullname = fullname;
		this.returnValues = null;
		this.timing = null;
		this.mockRunInformation = new MockRunInformation(fullname);
		this.listMockingVerifications = new LinkedList<>();
	}

	public void loadAttributeMockWithTiming(Duration timing, Val... returns) {
		this.timing = timing;
		this.returnValues = returns;
		this.listMockingVerifications.add(times(1));
	}

	@Override
	public Flux<Val> evaluate(Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args) {
		//ignore arguments
		
		this.mockRunInformation.saveCall(new MockCall());

		if (this.returnValues == null || this.timing == null) {
			throw new SaplTestException("Undefined internal state. Please report a bug to the library authors!");
		}

		return Flux.interval(this.timing).map(number -> this.returnValues[number.intValue()]).take(this.returnValues.length);
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
