
package io.sapl.spring.method.reactive;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

public class InvocationUtilTests {

	@Test
	void sneakyThrows() throws Throwable {
		var mock = mock(MethodInvocation.class);
		when(mock.proceed()).thenThrow(new IOException());
		assertThrows(IOException.class, () -> InvocationUtil.proceed(mock));
	}
}
