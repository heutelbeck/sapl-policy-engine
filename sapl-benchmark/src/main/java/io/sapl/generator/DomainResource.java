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
