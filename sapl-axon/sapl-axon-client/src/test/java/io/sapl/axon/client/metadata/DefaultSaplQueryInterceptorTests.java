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

package io.sapl.axon.client.metadata;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DefaultSaplQueryInterceptorTests {
	
	private ObjectMapper mapper;
	
	@BeforeEach
	void beforeEach() {
		mapper = new ObjectMapper();
	}
	
	@Test
	void when_NoSecurityContextPresent_then_ReturnNull(){
		var interceptor = new DefaultSaplQueryInterceptor(mapper);
		SecurityContextHolder.clearContext();
		
		assertTrue(interceptor.getSubjectMetadata().isEmpty());
		
	}
	
	@Test
	void when_SecurityContextPresent_then_ReturnSubject(){
		var secure = mock(SecurityContext.class);
		var user = new User("the username", "the password", true, true, true, true,
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		
		Authentication authentication = new UsernamePasswordAuthenticationToken(user, "the credentials");
		
		when(secure.getAuthentication()).thenReturn(authentication);
		
		SecurityContextHolder.setContext(secure);
		
		var interceptor = new DefaultSaplQueryInterceptor(mapper);
		ObjectNode subject = (ObjectNode) interceptor.getSubjectMetadata().get("subject");
		
		assertAll(
				() -> assertEquals(subject.findValue("name").asText(), "the username"),
				() -> assertFalse(subject.has("credentials")),
				() -> assertFalse(subject.has("password"))
				);
	}
	
	@Test
	void when_NoPrincipalSecurityContextPresent_then_ReturnSubject(){
		var secure = mock(SecurityContext.class);
		var anonymous = new AnonymousAuthenticationToken("key", "anonymous",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		
		when(secure.getAuthentication()).thenReturn(anonymous);
		
		SecurityContextHolder.setContext(secure);
		
		var interceptor = new DefaultSaplQueryInterceptor(mapper);
		ObjectNode subject = (ObjectNode) interceptor.getSubjectMetadata().get("subject");
		
		assertAll(
				() -> assertEquals(subject.findValue("name").asText(), "anonymous"),
				() -> assertEquals(subject.findValue("principal").asText(), "anonymous"),
				() -> assertFalse(subject.has("credentials")),
				() -> assertFalse(subject.has("password"))
				);
	}

}
