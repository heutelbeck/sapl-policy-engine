package io.sapl.server.ce.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
	private static final String LOGIN_PROCESSING_URL = "/login";
	private static final String LOGIN_FAILURE_URL = "/login";
	private static final String LOGIN_URL = "/login";
	private static final String LOGOUT_SUCCESS_URL = "/login";

	private static final String API_PATHS = "/api/**";

	@Value("${io.sapl.server-ce.key}")
	private String clientKey;

	@Value("${io.sapl.server-ce.secret}")
	private String clientSecret;

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
				.withUser(clientKey).password(clientSecret)
				.roles("PDP_CLIENT");
		// @formatter:on
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
