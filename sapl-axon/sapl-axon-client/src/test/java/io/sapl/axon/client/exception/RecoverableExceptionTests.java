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

package io.sapl.axon.client.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RecoverableExceptionTests {
	
	@Test
	void when_RecoverableException_then_ReturnRecoverableException() {
		RecoverableException  e = new RecoverableException();
	    assertEquals(RecoverableException.class, e.getClass());
	}
	
	@Test
	void when_ExceptionContainsString_then_ReturndExceptionWithMessage() {
		RecoverableException  e = new RecoverableException("testing");
	    assertEquals(RecoverableException.class, e.getClass());
	    assertEquals("testing", e.getMessage());	
	}
	
	@Test
	void when_ExceptionContainsStringAndCause_then_ReturndExceptionWithMessageAndCause() {
		Throwable expected = new Throwable("oops");
		RecoverableException  e = new RecoverableException("testing",expected);
	    assertEquals(RecoverableException.class, e.getClass());
	    assertEquals("testing", e.getMessage());
	    assertEquals(expected, e.getCause());
	}
	
	@Test
	void when_ExceptionContainsCause_then_ReturndExceptionWithause() {
		Throwable expected = new Throwable("oops");
		RecoverableException  e = new RecoverableException(expected);
	    assertEquals(RecoverableException.class, e.getClass());
	    assertEquals(expected, e.getCause());
	}


}
