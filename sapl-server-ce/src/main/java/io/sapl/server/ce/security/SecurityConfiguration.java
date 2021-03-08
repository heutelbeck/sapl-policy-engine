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
package io.sapl.server.ce.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.server.ce.service.ClientCredentialsService;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

	public static final String PDP_CLIENT_ROLE = "PDP_CLIENT";

	private static final String LOGIN_PROCESSING_URL = "/login";
	private static final String LOGIN_FAILURE_URL = "/login";
	private static final String LOGIN_URL = "/login";
	private static final String LOGOUT_SUCCESS_URL = "/login";
	private static final String API_PATHS = "/api/**";

	private final ClientCredentialsService clientCredentialsService;
	private final PasswordEncoder passwordEncoder;

	@Value("${io.sapl.server.admin-username}")
	private String adminUsername;

	@Value("${io.sapl.server.encoded-admin-password}")
	private String encodedAdminPassword;

	/**
	 * Require login to access internal pages and configure login form.
	 */
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// @formatter:off
		
		// use basic authentication for API
		http.csrf().disable()
				.authorizeRequests()
				.antMatchers(API_PATHS)
		        .authenticated()
		        .and()
		        .httpBasic()
		        .realmName("API");
		
		// use Spring Security for remaining urls
		http.csrf().disable()
				.requestCache().requestCache(new CustomRequestCache())
				.and().authorizeRequests()
				.requestMatchers(SecurityUtils::isFrameworkInternalRequest).permitAll()
				.anyRequest().authenticated()
				.and().formLogin().loginPage(LOGIN_URL).permitAll()
				.loginProcessingUrl(LOGIN_PROCESSING_URL)
				.failureUrl(LOGIN_FAILURE_URL)
				.and().logout().logoutSuccessUrl(LOGOUT_SUCCESS_URL);
		
		// @formatter:on
	}

	/**
	 * Allows access to static resources, bypassing Spring security.
	 */
	@Override
	public void configure(WebSecurity web) throws Exception {
		// @formatter:off
		web.ignoring().antMatchers(
				// Vaadin Flow static resources //
				"/VAADIN/**",
				// the standard favicon URI
				"/favicon.ico",
				// the robots exclusion standard
				"/robots.txt",
				// web application manifest //
				"/manifest.webmanifest", "/sw.js", "/offline-page.html",
				// (development mode) static resources //
				"/frontend/**",
				// (development mode) webjars //
				"/webjars/**",
				// (production mode) static resources //
				"/frontend-es5/**", "/frontend-es6/**");
		// @formatter:on
	}

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		// @formatter:off
		auth.inMemoryAuthentication()
			.withUser(adminUsername).password(encodedAdminPassword)
			.roles(PDP_CLIENT_ROLE);
		// @formatter:on

		auth.authenticationProvider(authProvider());
	}

	@Bean
	public DaoAuthenticationProvider authProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(clientCredentialsService);
		authProvider.setPasswordEncoder(passwordEncoder);
		return authProvider;
	}
}
