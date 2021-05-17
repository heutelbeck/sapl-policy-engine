package io.sapl.test.mocking;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

public interface AttributeMock {

	Flux<Val> evaluate();
	
	void assertVerifications();
	
	String getErrorMessageForCurrentMode();
}
