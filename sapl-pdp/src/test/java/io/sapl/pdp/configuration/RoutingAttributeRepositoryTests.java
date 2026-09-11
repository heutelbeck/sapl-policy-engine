package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ch.qos.logback.core.spi.ConfigurationEvent;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;

@ExtendWith(MockitoExtension.class)
class RoutingAttributeRepositoryTests {
	@Mock
	private PDPConfigurationSource source;
	
	@Captor
	private ArgumentCaptor<Consumer<ConfigurationEvent>> captor;
	
	private AttributeAccessContext context = new AttributeAccessContext(
			Value.ofObject(Map.of()),
			Value.ofObject(Map.of()),
			Value.ofObject(Map.of()));
			
	private AttributeFinderInvocation invocation = new AttributeFinderInvocation(
			"pdp-1", 
			"unknown-config", 
			"sapl.test", 
			List.of(), 
			Duration.ofSeconds(1), 
			Duration.ofSeconds(1),
			Duration.ofSeconds(1),
			0L,
			false,
			context);
	
	
	@Test
	@DisplayName("An observer with an unknown configId triggers an error and quetes the observation")
	void whenObserveCalledForUnknownConfigThenErrorTriggeredAndQueued() {
		try (var router     = new RoutingAttributeRepository(source)) {	
			List<Value> received = new ArrayList<>();
			var registration = router.observe(invocation, received::add);
				
			assertThat(received).hasSize(1);
			registration.close();
		}
	}
}
