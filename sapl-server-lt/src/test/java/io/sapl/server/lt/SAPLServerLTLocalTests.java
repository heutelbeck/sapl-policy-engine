package io.sapl.server.lt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(profiles = { "local", "quiet" })
class SAPLServerLTLocalTests {

	@Test
	void contextLoads() {
	}

}
