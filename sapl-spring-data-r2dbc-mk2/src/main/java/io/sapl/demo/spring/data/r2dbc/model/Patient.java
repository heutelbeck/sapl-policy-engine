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
package io.sapl.demo.spring.data.r2dbc.model;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;

@Data
public class Patient {
    @Id
    @Exclude
    Integer id;
    String  firstName;
    String  lastName;
    String  diagnosis;

    public Patient(String firstName, String lastName, String diagnosis) {
        this.firstName = firstName;
        this.lastName  = lastName;
        this.diagnosis = diagnosis;
    }

    public boolean hasId() {
        return id != null;
    }
}
