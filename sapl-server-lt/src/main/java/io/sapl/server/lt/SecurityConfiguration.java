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

import io.sapl.server.lt.apikey.ApiKeyAuthenticationConverter;
import io.sapl.server.lt.apikey.ApiKeyPayloadExchangeAuthenticationConverter;
import io.sapl.server.lt.apikey.ApiKeyReactiveAuthenticationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.PayloadInterceptorOrder;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadExchangeConverter;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadInterceptor;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;


@Slf4j
@Configuration
@EnableRSocketSecurity
@EnableWebFluxSecurity
@RequiredArgsConstructor
@SuppressWarnings("UnnecessarilyFullyQualified")
public class SecurityConfiguration {

	private final SAPLServerLTProperties pdpProperties;
	@Autowired
	private Environment env;

	String getJwtIssuerURI(){
		return env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
	}

	@Bean
	@Profile("local")
	SecurityWebFilterChain securityFilterChainLocal(ServerHttpSecurity http) {
		http = http.csrf().disable();

		if (pdpProperties.isAllowNoAuth()) {
			log.info("configuring NoAuth authentication");
			http = http.authorizeExchange()
					.pathMatchers("/**").permitAll()
					.and();
		} else {
			// any other request requires the user to be authenticated
			http = http
					.authorizeExchange()
					.anyExchange()
					.authenticated()
					.and();
		}

		if (pdpProperties.isAllowApiKeyAuth()) {
			log.info("configuring ApiKey authentication");
			var customAuthenticationWebFilter = new AuthenticationWebFilter(new ApiKeyReactiveAuthenticationManager());
			customAuthenticationWebFilter.setServerAuthenticationConverter(new ApiKeyAuthenticationConverter(pdpProperties));
			http = http.addFilterAt(customAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);
		}

		if (pdpProperties.isAllowBasicAuth()) {
			log.info("configuring BasicAuth authentication");
			http = http
					.httpBasic()
					.and();
		}

		if (pdpProperties.isAllowOauth2Auth()) {
			log.info("configuring Oauth2 authentication");
			http = http.oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt);
		}

		return http
				.formLogin().disable()
				.build();
	}

	@Bean
	@Profile("docker")
	SecurityWebFilterChain securityFilterChainDocker(ServerHttpSecurity http) {
		return http.csrf().disable().authorizeExchange().pathMatchers("/**").permitAll().and().build();
	}

	@Bean
	@Profile("local")
	MapReactiveUserDetailsService userDetailsServiceLocal() {
		UserDetails client = User.builder().username(pdpProperties.getKey()).password(pdpProperties.getSecret())
				.roles("PDP_CLIENT").build();
		return new MapReactiveUserDetailsService(client);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * The RSocketMessageHandler Bean (rsocketMessageHandler), activates parts of the Spring Security component
	 * model that let us inject the authenticated user into our handler methods (those annotated with @MessageMapping).
	 */
	@Bean
	RSocketMessageHandler rsocketMessageHandler(RSocketStrategies strategies) {
		var mh = new RSocketMessageHandler();
		mh.getArgumentResolverConfigurer().addCustomResolver(new AuthenticationPrincipalArgumentResolver());
		mh.setRSocketStrategies(strategies);
		return mh;
	}

	/**
	 * Decodes JSON Web Token (JWT) according to the configuration that was initialized by the
	 * OpenID Provider specified in the jwtIssuerURI.
	 */
	@Bean
	ReactiveJwtDecoder jwtDecoder() {
		if (pdpProperties.isAllowOauth2Auth()){
			return ReactiveJwtDecoders.fromIssuerLocation(getJwtIssuerURI());
		} else {
			return null;
		}
	}

	/**
	 * The PayloadSocketAcceptorInterceptor Bean (rsocketPayloadAuthorization) configures the Security Filter Chain for
	 * Rsocket Payloads. Supported Authentication Methods are: NoAuth, BasicAuth, Oauth2 (jwt) and ApiKey.
	 */
	@Bean
	PayloadSocketAcceptorInterceptor rsocketPayloadAuthorization(RSocketSecurity security) {
		security = security.authorizePayload(spec -> {
			if (pdpProperties.isAllowNoAuth()) {
				spec.anyExchange().permitAll();
			} else {
				spec.anyRequest().authenticated()
						.anyExchange().permitAll();
			}
		});

		// Configure Basic and Oauth Authentication
		UserDetailsRepositoryReactiveAuthenticationManager simpleManager = null;
		if ( pdpProperties.isAllowBasicAuth() ){
			simpleManager = new UserDetailsRepositoryReactiveAuthenticationManager(this.userDetailsServiceLocal());
			simpleManager.setPasswordEncoder(this.passwordEncoder());
		}

		JwtReactiveAuthenticationManager jwtManager = null;
		if ( pdpProperties.isAllowOauth2Auth()){
			jwtManager = new JwtReactiveAuthenticationManager(
					ReactiveJwtDecoders.fromIssuerLocation(getJwtIssuerURI())
			);
		}

		UserDetailsRepositoryReactiveAuthenticationManager finalSimpleManager = simpleManager;
		JwtReactiveAuthenticationManager finalJwtManager = jwtManager;
		AuthenticationPayloadInterceptor auth = new AuthenticationPayloadInterceptor(
			a -> {
				if ( finalSimpleManager != null && a instanceof UsernamePasswordAuthenticationToken ){
					return finalSimpleManager.authenticate(a);
				} else if ( finalJwtManager != null && a instanceof BearerTokenAuthenticationToken ) {
					return finalJwtManager.authenticate(a);
				} else {
					throw new IllegalArgumentException("Unsupported Authentication Type " + a.getClass().getSimpleName());
				}
			});
		auth.setAuthenticationConverter(new AuthenticationPayloadExchangeConverter());
		auth.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
		security.addPayloadInterceptor(auth);

		// Confgure ApiKey authentication
		if (pdpProperties.isAllowApiKeyAuth()) {
			ReactiveAuthenticationManager manager = new ApiKeyReactiveAuthenticationManager();
			AuthenticationPayloadInterceptor apikeyInterceptor = new AuthenticationPayloadInterceptor(manager);
			apikeyInterceptor.setAuthenticationConverter(new ApiKeyPayloadExchangeAuthenticationConverter(pdpProperties));
			apikeyInterceptor.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
			security.addPayloadInterceptor(apikeyInterceptor);
		}
		return security.build();

	}
}
