package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import io.sapl.reimpl.prp.PrpUpdateEventSource;
import io.sapl.reimpl.prp.filesystem.FileSystemPrpUpdateEventSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PrpUpdateEventSourceAutoConfiguration {

	private final SAPLInterpreter interpreter;
	private final EmbeddedPDPProperties pdpProperties;

	@Bean
	@ConditionalOnMissingBean
	public PrpUpdateEventSource prpUpdateSource() {
		var policiesFolder = pdpProperties.getPoliciesPath();
		if (pdpProperties.getPdpConfigType() == EmbeddedPDPProperties.PDPDataSource.FILESYSTEM) {
			log.info("creating embedded PDP sourcing and monitoring access policies from the filesystem: {}",
					policiesFolder);
			return new FileSystemPrpUpdateEventSource(policiesFolder, interpreter);
		}
		log.info("creating embedded PDP sourcing access policies from fixed bundled resources at: {}", policiesFolder);
		return new ResourcesPrpUpdateEventSource(policiesFolder, interpreter);
	}
}
