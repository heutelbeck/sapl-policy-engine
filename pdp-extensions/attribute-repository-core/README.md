# Attribute Repository Core Extension

## Overview

This extension adds a routing layer to the SAPL Node. It is used by attribute
repositories that store their attributes in a persistent database and routes
each PDP ID to its own database.

You do not add this module yourself. It is pulled in as a dependency by
`attribute-repository-postgres`, `attribute-repository-mongo` or
`attribute-repository-redis`.

## Configuration

Each PDP folder can contain an `ext-attributerepository.json`. The field `type`
selects the database, all other fields depend on the database and are described
in its README. A PDP without this file uses an in-memory repository.

An example for a minimal configuration of Redis is:
```json
{
  "type": "redis",
  "host": "localhost",
  "port": 6379
}
```

An example for a minimal configuration of Postgres is:

```json
{
  "type": "postgres",
  "host": "localhost",
  "port": 5432,
  "username": "sapl",
  "password": "<password>",
  "database": "sapl"
}
```

An example for a minimal configuration of Mongo is:

```json
{
  "type": "mongo",
  "host": "localhost",
  "port": 27017,
  "database": "sapl"
}
```



