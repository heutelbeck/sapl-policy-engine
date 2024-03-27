/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.springdatar2dbcdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;

import org.springframework.security.core.context.SecurityContextHolder;

import reactor.core.publisher.Mono;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableReactiveSaplMethodSecurity
public class SecurityConfig implements WebFilter {

	@Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

		return http
				.authorizeExchange(
						exchange -> exchange.pathMatchers("/public/**").permitAll().anyExchange().authenticated())
				.formLogin(withDefaults()).logout(logout -> logout.logoutUrl("/logout")).build();
	}

	@Bean
	MapReactiveUserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		UserDetails testUser1 = User.withUsername("admin").password(passwordEncoder.encode("admin")).roles("ADMIN")
				.authorities("ROLE_ADMIN").disabled(false).build();

		UserDetails testUser2 = User.withUsername("user").password(passwordEncoder.encode("user")).roles("USER")
				.authorities("ROLE_USER").disabled(false).build();

		return new MapReactiveUserDetailsService(testUser1, testUser2);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return exchange.getSession()
				.map(session -> session.getAttributeOrDefault(
						WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME, null))
				.flatMap(securityContext -> {
					if (securityContext != null) {
						SecurityContextHolder.setContext((SecurityContext) securityContext);
					}
					return chain.filter(exchange);
				}).switchIfEmpty(Mono.empty());
	}
}