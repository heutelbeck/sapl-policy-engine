package io.sapl.server.lt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(profiles = {"docker", "quiet"})
class SAPLServerLTDockerTests {

	@Test
	void contextLoads() {
	}

}
