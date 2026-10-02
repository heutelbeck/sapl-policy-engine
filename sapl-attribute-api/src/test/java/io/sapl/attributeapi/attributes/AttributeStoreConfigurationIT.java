package io.sapl.attributeapi.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.backend.AttributeKey;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@DisplayName("AttributeStoreConfiguration with Postgres in embedded mode")
class AttributeStoreConfigurationIT {
	@Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

	@Test
	@DisplayName("When using an embedded store and Postgres as backend then publish an attribute and validate the value")
	void whenEmbeddedConfigHasPostgresThenPublishAnAttributeAndGetTheValue() {
		var config = new AttributeStoreConfiguration();
		
		var nodeConfig = ObjectValue.builder()
                .put("type", Value.of("postgres"))
                .put("host", Value.of(postgres.getHost()))
                .put("port", Value.of(postgres.getMappedPort(5432)))
                .put("database", Value.of(postgres.getDatabaseName()))
                .put("username", Value.of(postgres.getUsername()))
                .put("password", Value.of(postgres.getPassword()))
                .build();
		
		Function<String, Optional<Value>> configResolver = pdpId -> Optional.of(nodeConfig);
        var attributeStore = config.embeddedRoutingAttributeStore(configResolver);
        var key = new AttributeKey(Value.of("alice"), "test.attribute", List.of());

        attributeStore.publish(key, Value.of("aValue"), "aTenant");

        assertThat(attributeStore.get(key, "aTenant")).isEqualTo(Value.of("aValue"));
	}
}
