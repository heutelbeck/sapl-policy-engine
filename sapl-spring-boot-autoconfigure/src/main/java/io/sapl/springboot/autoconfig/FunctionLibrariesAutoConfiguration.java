package io.sapl.springboot.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.functions.GeoFunctionLibrary;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FunctionLibrariesAutoConfiguration {

	@Configuration
	@ConditionalOnClass(io.sapl.functions.GeoFunctionLibrary.class)
	public static class GeoConfiguration {

		@Bean
		public GeoFunctionLibrary geoFunctionLibrary() {
			LOGGER.info("Geo function library present. Loading.");
			return new GeoFunctionLibrary();
		}

	}

}
