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
package io.sapl.server.lt;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SaplServerLTApplicationTests {

	@Test
	void main() {
		try (MockedStatic<SpringApplication> theMock = mockStatic(SpringApplication.class)) {
			theMock.when(this::runNoArgs).thenReturn(null);
			SAPLServerLTApplication.main(new String[] {});
			theMock.verify(this::runNoArgs, times(1));
		}
	}

	private void runNoArgs() {
		SpringApplication.run(SAPLServerLTApplication.class);
	}

}