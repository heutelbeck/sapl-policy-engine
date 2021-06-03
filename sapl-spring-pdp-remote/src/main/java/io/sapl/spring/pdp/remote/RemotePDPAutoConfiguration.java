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

import javax.net.ssl.SSLException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(RemotePDPProperties.class)
public class RemotePDPAutoConfiguration {

	private final RemotePDPProperties configuration;

	@Bean
	@ConditionalOnMissingBean
	public PolicyDecisionPoint policyDecisionPoint() throws SSLException {
		log.info("Binding to remote PDP server: {}", configuration.getHost());
		if (configuration.isIgnoreCertificates()) {
			log.warn("INSECURE SSL SETTINGS! This demo uses an insecure SslContext for "
					+ "testing purposes only. It will accept all certificates. "
					+ "This is only for testing local servers with self-signed certificates easily. "
					+ "NERVER USE SUCH A CONFIURATION IN PRODUCTION!");
			var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
			return new RemotePolicyDecisionPoint(configuration.getHost(), configuration.getKey(),
					configuration.getSecret(), sslContext);

		} else {
			return new RemotePolicyDecisionPoint(configuration.getHost(), configuration.getKey(),
					configuration.getSecret());
		}
	}

}
