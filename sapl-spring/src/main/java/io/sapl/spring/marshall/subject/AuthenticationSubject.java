package io.sapl.spring.marshall.subject;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.sapl.spring.marshall.Subject;
import lombok.Value;

@Value
public class AuthenticationSubject implements Subject {
	
	String name;
	Collection<? extends GrantedAuthority> authorities;
	Object details;
	 
	
	public AuthenticationSubject (Authentication authentication) {
		this.name = authentication.getName();
		this.authorities = authentication.getAuthorities();
		this.details = authentication.getDetails();
	}
	
	public AuthenticationSubject (Authentication authentication, Object details) {
		this.name = authentication.getName();
		this.authorities = authentication.getAuthorities();
		this.details = details;
	}

}
