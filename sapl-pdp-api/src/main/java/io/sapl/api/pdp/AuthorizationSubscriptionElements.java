/*
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure holding IDs for the elements of an
 * {@link AuthorizationSubscription} SAPL authorization subscription).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(NON_EMPTY)
public class AuthorizationSubscriptionElements {
	
	@NotNull
	Integer subjectId;
	@NotNull
	Integer actionId;
	@NotNull
	Integer resourceId;
	@NotNull
	Integer environmentId;

}
