package io.sapl.server.ce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(profiles = { "demo", "quiet" })
public class SAPLServerCEDemoTests {

	@Test
	void contextLoads() {
	}

}
