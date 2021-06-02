/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.api.pdp.PolicyDecisionPoint;

class RemotePDPAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RemotePDPAutoConfiguration.class));

	@Test
	void whenValidPropertiesArePresent_thenTheRemotePdpIsPresent() {
		contextRunner.withPropertyValues(
					"io.sapl.pdp.remote.host=https://localhost:8443",
					"io.sapl.pdp.remote.key=aKey", 
					"io.sapl.pdp.remote.secret=aSecret"
				).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
				});
	}
	@Test
	void whenValidPropertiesArePresentWithIgnore_thenTheRemotePdpIsPresent() {
		contextRunner.withPropertyValues(
					"io.sapl.pdp.remote.host=https://localhost:8443",
					"io.sapl.pdp.remote.key=aKey", 
					"io.sapl.pdp.remote.secret=aSecret",
					"io.sapl.pdp.remote.ignoreCertificates=true"
				).run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
				});
	}
}
