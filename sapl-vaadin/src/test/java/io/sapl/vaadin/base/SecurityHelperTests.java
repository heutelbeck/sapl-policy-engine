package io.sapl.vaadin.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.node.ArrayNode;

class SecurityHelperTests {

	@Test
	void test_whenGetSubject_then_UsernameAttributeIsSet() {
		//GIVEN
		MockedStatic<SecurityContextHolder> contextHolderMock = mockStatic(SecurityContextHolder.class);
		SecurityContext contextMock = mock(SecurityContext.class);
		Authentication authMock = mock(Authentication.class);

		contextHolderMock.when(SecurityContextHolder::getContext).thenReturn(contextMock);
		when(contextMock.getAuthentication()).thenReturn(authMock);
		when(authMock.getName()).thenReturn("user");

		//WHEN
		String username = SecurityHelper.getSubject().get("username").asText();

		//THEN
		assertEquals("user", username);

		contextHolderMock.close();
	}

	@Test
	void test_whenGetSubject_then_RolesAttributeIsSet() {
		//GIVEN
		MockedStatic<SecurityContextHolder> contextHolderMock = mockStatic(SecurityContextHolder.class);
		SecurityContext contextMock = mock(SecurityContext.class);
		Authentication authMock = mock(Authentication.class);

		contextHolderMock.when(SecurityContextHolder::getContext).thenReturn(contextMock);
		when(contextMock.getAuthentication()).thenReturn(authMock);
		when(authMock.getName()).thenReturn("user");

		when(authMock.getAuthorities()).thenAnswer((invocationOnMock) -> {
			@SuppressWarnings("unchecked")
			Collection<? extends GrantedAuthority> collection = (Collection<? extends GrantedAuthority>)mock(Collection.class);
			@SuppressWarnings("unchecked")
			Stream<? extends GrantedAuthority> stream = (Stream<? extends GrantedAuthority>)mock(Stream.class);
			doReturn(stream).when(collection).stream();
			doReturn(stream).when(stream).map(any());
			ArrayList<String> roles = new ArrayList<>();
			roles.add("userRole");
			when(stream.collect(any())).thenReturn(roles);
			return collection;
		});

		//WHEN
		ArrayNode roles = (ArrayNode)SecurityHelper.getSubject().get("roles");

		//THEN
		assertEquals( "userRole", roles.get(0).asText() );

		contextHolderMock.close();
	}

	@Test
	void test_whenGetUsernameAndGetAuthenticationReturnsNull_then_NullIsReturned() {
		//GIVEN
		MockedStatic<SecurityContextHolder> contextHolderMock = mockStatic(SecurityContextHolder.class);
		SecurityContext contextMock = mock(SecurityContext.class);
		Authentication authMock = mock(Authentication.class);

		contextHolderMock.when(SecurityContextHolder::getContext).thenReturn(contextMock);
		when(contextMock.getAuthentication()).thenReturn(null);
		when(authMock.getName()).thenReturn("user");

		//WHEN
		String username = SecurityHelper.getUsername();

		//THEN
		assertNull(username);

		contextHolderMock.close();
	}


	@Test
	void test_whenGetUserRolesAndGetAuthenticationReturnsNull_then_EmptyListIsReturned() {
		//GIVEN
		MockedStatic<SecurityContextHolder> contextHolderMock = mockStatic(SecurityContextHolder.class);
		SecurityContext contextMock = mock(SecurityContext.class);

		contextHolderMock.when(SecurityContextHolder::getContext).thenReturn(contextMock);
		when(contextMock.getAuthentication()).thenReturn(null);

		//WHEN
		List<String> userRoles = SecurityHelper.getUserRoles();

		//THEN
		assertEquals(0, userRoles.size());

		contextHolderMock.close();
	}
}
