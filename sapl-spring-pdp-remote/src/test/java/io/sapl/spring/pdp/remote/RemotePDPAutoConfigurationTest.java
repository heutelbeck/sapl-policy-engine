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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.sapl.pdp.remote.RemotePolicyDecisionPoint;

@ExtendWith(SpringExtension.class)
@TestPropertySource("classpath:test-config.properties")
@EnableConfigurationProperties(value = RemotePDPConfiguration.class)
@ContextConfiguration(classes = RemotePDPAutoConfiguration.class)
class RemotePDPAutoConfigurationTest {

	@Autowired
	private RemotePDPConfiguration properties;

	@Autowired
	private RemotePolicyDecisionPoint pdp;

	@Test
	void validateAutoConfiguration() {
		assertThat(properties.getHost(), is("https://localhost:8443"));
		assertThat(properties.getKey(), is("aKey"));
		assertThat(properties.getSecret(), is("aSecret"));
		assertThat(pdp, notNullValue());
	}

}
