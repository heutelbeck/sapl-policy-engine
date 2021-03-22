package io.sapl.server.ce;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SaplServerLTApplicationTests {

	@Test
	void main() {
		try (MockedStatic<SpringApplication> theMock = mockStatic(SpringApplication.class)) {
			theMock.when(this::runNoArgs).thenReturn(null);
			PdpServerApplication.main(new String[] {});
			theMock.verify(this::runNoArgs, times(1));
		}
	}

	private void runNoArgs() {
		SpringApplication.run(PdpServerApplication.class, new String[] {});
	}

}