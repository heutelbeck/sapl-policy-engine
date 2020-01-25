/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
