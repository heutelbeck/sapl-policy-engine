# Attribute Repository Core Extension

## Overview

This module is the base of the attribute repository extensions. It adds a routing layer to the PDP that assigns an attribute repository to each PDP ID. The module cannot be used on its own. The modules `attribute-repository-postgres`, `attribute-repository-mongo`, `attribute-repository-redis` bring it automatically as dependency.

## How the module works

The module is adding the following functionality to the PDP:

- Each PDP folder can contain an `ext-attributerepository.json` file. Its field `type` determines which database extension is used. The READMEs of the other modules contain concrete examples. The configuration of the secrets and the file above will be merged into one configuration for each PDP ID. A PDP without these files is using an `InMemoryAttributeRepository`.
- The attribute repository is managed by a special routing attribute repository. It maintains the configuration, routes requests to the right database and retries with backoff if the database is not reachable when the repository is created.
- The database extensions are found via the Java `ServiceLoader`. No further configuration is needed.

## Adding a database extension

A new database can be supported by implementing the `AttributeRepositoryProvider` interface. The following methods need to be implemented:

| Method | To-Do |
|--------|-------|
| `type()` | Name used in the field `type` of the `ext-attributerepository.json` |
| `validate(config)` | Validates the configuration before the attribute repository is created | 
| `create(config, pdpId)` | Creates the repository for a PDP ID | 

The provider has to be registered in the file `src/main/resources/META-INF/services/io.sapl.extensions.attributerepository.AttributeRepositoryProvider` with its fully qualified class name.