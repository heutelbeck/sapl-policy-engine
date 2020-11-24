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

import java.io.Serializable;
import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;

@Repository
public interface ClientCredentialsRepository extends CrudRepository<ClientCredentials, Long>, Serializable {
	/**
	 * Returns all instances of the {@link ClientCredentials}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<ClientCredentials> findAll();

	/**
	 * Gets the {@link PublishedSaplDocument}s with a specific name.
	 * 
	 * @param name the name
	 * @return the {@link PublishedSaplDocument}s
	 */
	@Query(value = "SELECT c FROM ClientCredentials c WHERE c.key = :key")
	Collection<ClientCredentials> findByKey(@Param(value = "key") String key);
}
