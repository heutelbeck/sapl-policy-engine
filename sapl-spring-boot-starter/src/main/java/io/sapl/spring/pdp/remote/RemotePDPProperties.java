/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pdp.remote;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl.pdp.remote")
public class RemotePDPProperties implements Validator {

    private boolean enabled = false;

    // general connection settings
    @NotEmpty
    private String  type               = "http";
    private boolean ignoreCertificates = false;

    // http
    @URL
    private String host = "";

    // basic authentication
    private String key    = "";
    private String secret = "";

    // api_key authentication
    private String apiKey = "";

    @Override
    public boolean supports(Class<?> clazz) {
        return RemotePDPProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        RemotePDPProperties properties = (RemotePDPProperties) target;
        if ("http".equals(properties.type)) {
            ValidationUtils.rejectIfEmpty(errors, "host", "requires-host", "host containing http url is required");
        } else {
            errors.rejectValue("type", "type-invalid", new String[] { properties.type },
                    "Invalid type specified, valid value is \"http\"");
        }

        // ensure that exactly one authentication mecanisn is specified
        if (apiKey.isEmpty() ^ key.isEmpty()) {
            if (!key.isEmpty()) {
                ValidationUtils.rejectIfEmpty(errors, "secret", "requires-secret", "\"secret\" must not be empty");
            }
        } else {
            errors.rejectValue("key", "key-invalid", new String[] { properties.key },
                    "At least one authentication mechanismn needed \"key\" and \"secret\" or \"api_key\"");
        }

    }
}
