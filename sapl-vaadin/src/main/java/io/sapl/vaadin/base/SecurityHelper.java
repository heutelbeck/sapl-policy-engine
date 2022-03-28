package io.sapl.vaadin.base;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityHelper {
	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	/**
	 * This method reads the name of the user who is logged in.
	 *
	 * @return the name of the user
	 */
	public static String getUsername() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if ( authentication != null ) {
			return authentication.getName();
		} else {
			return null;
		}
	}

	/**
	 * This method reads the roles of the user who is logged in.
	 *
	 * @return the roles of the user as a list
	 */
	public static List<String> getUserRoles() {
		Authentication userAuthentication = SecurityContextHolder.getContext().getAuthentication();
		if (userAuthentication != null) {
			return userAuthentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
					.collect(Collectors.toList());
		} else {
			return new ArrayList<>();
		}
	}

	public static ObjectNode getSubject() {
		var subject = JSON.objectNode();
		subject.put("username", SecurityHelper.getUsername());
		var rolesNode = JSON.arrayNode();
		for (String role : SecurityHelper.getUserRoles()) {
			rolesNode.add(role);
		}
		subject.set("roles", rolesNode);
		return subject;
	}
}
