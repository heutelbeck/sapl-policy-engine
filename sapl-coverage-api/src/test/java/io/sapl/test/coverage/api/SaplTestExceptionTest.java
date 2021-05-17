package io.sapl.test.coverage.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class SaplTestExceptionTest {

	@Test
	void test() {
		assertThrows(SaplTestException.class, () -> {	
			throw new SaplTestException();
		});
		
		assertThrows(SaplTestException.class, () -> {	
			throw new SaplTestException("Test");
		});
		
		assertThrows(SaplTestException.class, () -> {	
			throw new SaplTestException("Test", new IOException());
		});
	}

}
