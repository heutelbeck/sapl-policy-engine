package io.sapl.spring.marshall.subject;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.sapl.api.pdp.marshall.Subject;
import lombok.Value;

@Value
public class AuthoritiesSubject implements Subject {

	List<String> authorities;

	public AuthoritiesSubject(Authentication authentication) {
		authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList());
	}

}
