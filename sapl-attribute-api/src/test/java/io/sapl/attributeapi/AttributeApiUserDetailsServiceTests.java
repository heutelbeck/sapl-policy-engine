package io.sapl.attributeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import io.sapl.attributeapi.auth.AttributeApiUserDetailsService;

class AttributeApiUserDetailsServiceTests {
	@Test
	@DisplayName("returns matching user details for a known basic authentication user")
	void whenUserExistsThenReturnUserDetails() {
		var basic = new AttributeApiSecurityProperties.Basic();
		
		basic.setUsername("alice");
		basic.setSecret("$argon2id$v=19$m=16384,t=2,p=1$TAKAbikXvm+RzKPUysWxog$Iv+pnfhWQst5SgL63Bu5VML6EruahgZxutrCBcntuAU"); // bob
		
		var user = new AttributeApiSecurityProperties.UserEntry();
		user.setTenantId("tenant-a");
		user.setBasic(basic);
		
		var properties = new AttributeApiSecurityProperties();
		properties.setUsers(List.of(user));
		
		var details = new AttributeApiUserDetailsService(properties).loadUserByUsername("alice");
		
		assertThat(details.getUsername()).isEqualTo("alice");
		assertThat(details.getPassword())
        .isEqualTo("$argon2id$v=19$m=16384,t=2,p=1$TAKAbikXvm+RzKPUysWxog$Iv+pnfhWQst5SgL63Bu5VML6EruahgZxutrCBcntuAU");
	}
	
	@Test
    @DisplayName("throws when no user matches the given username")
    void whenNoUserMatchesThenThrows() {
        var properties = new AttributeApiSecurityProperties();
        properties.setUsers(List.of());

        var service = new AttributeApiUserDetailsService(properties);

        assertThatThrownBy(() -> service.loadUserByUsername("unknownUser"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknownUser");
    }

    @Test
    @DisplayName("skips API-key-only users instead of NPE-ing on missing basic block")
    void whenUserHasNoBasicAuthThenNotMatched() {
        var apiKeyOnlyUser = new AttributeApiSecurityProperties.UserEntry();

        var properties = new AttributeApiSecurityProperties();
        properties.setUsers(List.of(apiKeyOnlyUser));

        var service = new AttributeApiUserDetailsService(properties);

        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
