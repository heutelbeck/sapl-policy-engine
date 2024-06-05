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
package io.sapl.server.ce.model.pdpconfiguration;

import java.io.Serializable;
import java.util.Collection;

import org.springframework.data.repository.CrudRepository;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.NonNull;

/**
 * Interface for a repository for accessing the selected
 * {@link PolicyDocumentCombiningAlgorithm}.
 */
public interface SelectedCombiningAlgorithmRepository
        extends CrudRepository<SelectedCombiningAlgorithm, Long>, Serializable {
    /**
     * Returns all instances of the {@link SelectedCombiningAlgorithm}s.
     *
     * @return the instances
     */
    @Override
    @NonNull
    Collection<SelectedCombiningAlgorithm> findAll();
}
