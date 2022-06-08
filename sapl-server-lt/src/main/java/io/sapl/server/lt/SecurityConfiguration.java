/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

	private final SAPLServerLTProperties pdpProperties;

	@Bean
	@Profile("local")
	public SecurityWebFilterChain securityFilterChainLocal(ServerHttpSecurity http) {
		return http
				.csrf()
					.disable()
				.authorizeExchange()
					// any other request requires the user to be authenticated
					.anyExchange().authenticated()
				.and()
					.httpBasic()
				.and()
					.formLogin().disable()
				.build();
	}

	@Bean
	@Profile("docker")
	public SecurityWebFilterChain securityFilterChainDocker(ServerHttpSecurity http) {
		return http.csrf().disable().authorizeExchange().pathMatchers("/**").permitAll().and().build();
	}

	@Bean
	@Profile("local")
	public MapReactiveUserDetailsService userDetailsServiceLocal() {
		UserDetails client = User.builder().username(pdpProperties.getKey()).password(pdpProperties.getSecret())
				.roles("PDP_CLIENT").build();
		return new MapReactiveUserDetailsService(client);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
