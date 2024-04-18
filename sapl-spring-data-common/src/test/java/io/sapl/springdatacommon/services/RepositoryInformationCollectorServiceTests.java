/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.springdatacommon.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.core.RepositoryInformation;

class RepositoryInformationCollectorServiceTests {
	
	RepositoryInformation repositoryInformationMock = mock(RepositoryInformation.class, RETURNS_DEEP_STUBS);

    @Test
    void when_add_then_addNewRepositoryInformation() {
    	// GIVEN
        var repositoryInformationMock = mock(RepositoryInformation.class);
        var service = new RepositoryInformationCollectorService();
        
        // WHEN
        service.add(repositoryInformationMock);
        var repositories = service.getRepositories();
        
        // THEN
        assertEquals(1, repositories.size());
        assertEquals(repositoryInformationMock, repositories.iterator().next());
    }

}
