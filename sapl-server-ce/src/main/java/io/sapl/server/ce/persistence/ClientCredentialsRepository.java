/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.ClientCredentials;

@Repository
public interface ClientCredentialsRepository extends CrudRepository<ClientCredentials, Long> {
	/**
	 * Returns all instances of the {@link ClientCredentials}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<ClientCredentials> findAll();
}
