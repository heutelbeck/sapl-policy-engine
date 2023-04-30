package io.sapl.server.lt.apikey;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;

import reactor.core.publisher.Mono;

public class ApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {

	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		return Mono.fromSupplier(() -> {
			if (authentication != null && authentication.getCredentials() != null) {
				authentication.setAuthenticated(true);
			}
			return authentication;
		});
	}
}
