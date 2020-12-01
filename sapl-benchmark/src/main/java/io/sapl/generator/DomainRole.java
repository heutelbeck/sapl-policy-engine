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
package io.sapl.generator;

import io.sapl.generator.DomainPolicy.DomainPolicyAdvice;
import io.sapl.generator.DomainPolicy.DomainPolicyBody;
import io.sapl.generator.DomainPolicy.DomainPolicyObligation;
import io.sapl.generator.DomainPolicy.DomainPolicyTransformation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class DomainRole {

    private final String roleName;
    private final boolean generalUnrestrictedAccess;
    private final boolean generalReadAccess;
    private final boolean generalCustomAccess;
    private final boolean extensionRequired;


    @Getter
    @Builder
    @AllArgsConstructor
    public static class ExtendedDomainRole {

        private DomainRole role;

        @Builder.Default
        private DomainPolicyBody body = null;
        @Builder.Default
        private DomainPolicyObligation obligation = null;
        @Builder.Default
        private DomainPolicyAdvice advice = null;
        @Builder.Default
        private DomainPolicyTransformation transformation = null;


        public ExtendedDomainRole(DomainRole role) {
            this.role = role;
        }

        public boolean isBodyPresent() {

            return this.body != null;
        }

        public boolean isObligationPresent() {
            return this.obligation != null;
        }

        public boolean isAdvicePresent() {
            return this.advice != null;
        }

        public boolean isTransformationPresent() {
            return this.transformation != null;
        }


    }

}
