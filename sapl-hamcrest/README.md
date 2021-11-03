# SAPL Hamcrest

This module contains an extension for the Hamcrest matching libraries with useful matchers for testing SAPL specific objects, i.e., for `Val` and `AuthorizationDecision`.

This library works well together with the [Spotify Hamcrest matchers 
for JSON objects](https://github.com/spotify/java-hamcrest#json-matchers).

## Example:

```java
	@Test
	public void when_permit_then_isPermit() {
		var authzDecision = AuthorizationDecision.DENY;
		assertThat(authzDecision, isPermit());
	}

```

Example output:

```
java.lang.AssertionError: 
Expected: the decision is PERMIT
     but: was decision of DENY
```

