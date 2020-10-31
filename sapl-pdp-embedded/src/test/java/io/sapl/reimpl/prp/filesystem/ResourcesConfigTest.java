package io.sapl.reimpl.prp.filesystem;

import java.util.logging.Level;

import org.junit.Test;

import io.sapl.pdp.embedded.config.resources.ResourcesPDPConfigurationProvider;
import reactor.core.publisher.SignalType;

public class ResourcesConfigTest {
	@Test
	public void doTest() throws InterruptedException {
		var configProvider = new ResourcesPDPConfigurationProvider("/policies");
		configProvider.getDocumentsCombinator().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		configProvider.getVariables().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		configProvider.dispose();
	}
}
