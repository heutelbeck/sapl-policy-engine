package io.sapl.springboot.autoconfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.pip.GeoPolicyInformationPoint;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.pip.http.HttpPolicyInformationPoint;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class PolicyInformationPointsAutoConfiguration {

	@Configuration
	@ConditionalOnClass(io.sapl.pip.http.HttpPolicyInformationPoint.class)
	public static class HTTPConfiguration {

		@Bean
		public HttpPolicyInformationPoint httpPolicyInformationPoint() {
			LOGGER.info("HTTP PIP present. Loading.");
			return new HttpPolicyInformationPoint();
		}

	}

	@Configuration
	@ConditionalOnClass(io.sapl.interpreter.pip.GeoPolicyInformationPoint.class)
	public static class GeoConfiguration {

		@Bean
		public GeoPolicyInformationPoint geoPolicyInformationPoint() {
			LOGGER.info("GEO PIP present. Loading.");
			return new GeoPolicyInformationPoint();
		}

	}

	@Configuration
	@ConditionalOnClass(io.sapl.pip.ClockPolicyInformationPoint.class)
	public static class ClockConfiguration {

		@Bean
		public ClockPolicyInformationPoint clockPolicyInformationPoint() {
			LOGGER.info("Clock PIP present. Loading.");
			return new ClockPolicyInformationPoint();
		}

	}

}
