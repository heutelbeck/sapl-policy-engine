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

import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class DomainResource {

    private final String resourceName;
    private final boolean unrestricted;
    private final boolean extensionRequired;

    private List<DomainRole> fullAccessRoles = new ArrayList<>();
    private List<DomainRole> readAccessRoles = new ArrayList<>();
    private List<DomainRole> customAccessRoles = new ArrayList<>();


    public DomainResource(String resourceName) {
        this(resourceName, false, false);
    }

    public void clearResourceAccessRoles(){
        fullAccessRoles.clear();
        readAccessRoles.clear();
        customAccessRoles.clear();
    }

    public static class DomainResources {
        public static DomainResource findByName(List<DomainResource> resourceList, String resourceName) {
            return resourceList.stream()
                    .filter(domainResource -> domainResource.getResourceName().equalsIgnoreCase(resourceName))
                    .findFirst().orElseThrow(() -> new RuntimeException("resource not present: " + resourceName));
        }
    }


}
