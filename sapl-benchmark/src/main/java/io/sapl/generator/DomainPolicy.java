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

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class DomainPolicy {

    private final String policyName;
    private final String policyContent;
    private final String fileName;

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyAdvice {

        private final String advice;
    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyBody {

        private final String body;

    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyObligation {

        private final String obligation;
    }

    @Data
    @RequiredArgsConstructor
    public static class DomainPolicyTransformation {

        private final String transformation;
    }
}
