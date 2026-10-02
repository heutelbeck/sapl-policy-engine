package io.sapl.attributeapi.attributes;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.AttributeStorageProperties.BackendType;

class AttributeStoreConfigurationTests {
	@Test
	@DisplayName("when a valid Postgres configuration is given and the optional table name is missing then the default is used")
	void whenPostgresConfigWithoutTableNameThenDefaultIsUsed() {
		var node = ObjectValue.builder()
				.put("type", Value.of("postgres"))
				.put("host", Value.of("localhost"))
				.put("port", Value.of(5432))
				.put("database", Value.of("sapl"))
				.put("username", Value.of("sapl"))
				.put("password", Value.of("password")).build();
		
		var config = new AttributeStoreConfiguration().toBackendConfig(node);
		
		assertThat(config.getType()).isEqualTo(BackendType.POSTGRES);
		assertThat(config.getPostgres().getTableName()).isEqualTo("attributes");
	}
	
	@Test
	@DisplayName("when a valid Mongo configuration is given and the optional collection name is missing then the default is used")
	void whenMongoConfigWithoutCollectionNameThenDefaultIsUsed() {
		var node = ObjectValue.builder()
				.put("type", Value.of("mongo"))
				.put("host", Value.of("localhost"))
				.put("port", Value.of(27001))
				.put("database", Value.of("sapl"))
				.put("username", Value.of("sapl"))
				.put("password", Value.of("password")).build();
		
		var config = new AttributeStoreConfiguration().toBackendConfig(node);
		
		assertThat(config.getType()).isEqualTo(BackendType.MONGO);
		assertThat(config.getMongo().getCollectionName()).isEqualTo("attributes");
	}
	
	@Test
	@DisplayName("when a valid Redis configuration is given and the optional db is missing then the default is used")
	void whenRedisConfigWithoutDbThenDefaultIsUsed() {
		var node = ObjectValue.builder()
				.put("type", Value.of("redis"))
				.put("host", Value.of("localhost"))
				.put("port", Value.of(6379))
				.put("password", Value.of("password")).build();
		
		var config = new AttributeStoreConfiguration().toBackendConfig(node);
		
		assertThat(config.getType()).isEqualTo(BackendType.REDIS);
		assertThat(config.getRedis().getDatabase()).isZero();
	}
}
