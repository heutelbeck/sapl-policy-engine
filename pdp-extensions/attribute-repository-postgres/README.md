# PostgreSQL Attribute Repository Extension

## Overview

The Postgres attribute repository extension stores the attributes of a PDP in a PostgreSQL database. All SAPL Node or PDP instances that share the database see the same attributes and are notified about changes via `LISTEN` and `NOTIFY`. It is intended to be used with the Attribute API Server, either embedded in the SAPL Node or as a standalone server.

## Setup

The database must be prepared before its first use. The setup is separated into mandatory, recommended and optional steps. The PDP ignores expired attributes and deletes them from the table when their TTL expires while it is running. Attributes that expire while no PDP is running remain in the table. The extension `pg_cron` is highly recommended to remove them with a cleanup job.

### Mandatory steps

The following steps must be executed before usage: 

1. Create a database e.g. `CREATE DATABASE sapl_attributes;`

2. If the table is created automatically, the role needs the privilege to create tables in the schema `GRANT CREATE, USAGE ON SCHEMA public TO <role>;`. Since PostgreSQL 15 this privilege is no longer granted by default.

### Recommended steps

The following steps are highly recommended to execute before usage:

1. Add the line `cron.database_name = 'sapl_attributes'` to the file `postgresql.conf`. This sets the default name for the cronjob database.

2. Add the extension `shared_preload_libraries = 'pg_cron'` to the file `postgresql.conf`. A restart of Postgres is required to activate the extension. Also add `cron.use_background_workers = on` to `postgresql.conf` if local connections require a password. Otherwise `pg_cron` runs each job over a new connection without a password and the job fails.

3. Connect as a superuser to the database `sapl_attributes` and activate the extension: `CREATE EXTENSION pg_cron;`

4. Grant a PostgreSQL role privileges to use the schema `GRANT USAGE ON SCHEMA cron TO <role>;`.

The cleanup job is scheduled automatically on startup with `cron.schedule`. It runs every minute as the application role in the database `cron.database_name`. The application role needs neither superuser nor `CREATEDB` privileges.

### Optional steps

The database table will be automatically created if missing during startup. If you want to create the table manually, you can use the following statement:

```sql
    CREATE TABLE IF NOT EXISTS attributes (
      id         BIGINT      GENERATED ALWAYS AS IDENTITY,
      pdp_id     TEXT        NOT NULL,
      name       TEXT        NOT NULL,
      entity     JSONB,
      arguments  JSONB       NOT NULL DEFAULT '[]',
      value      JSONB       NOT NULL,
      expires_at TIMESTAMPTZ,
      CONSTRAINT attributes_pdp_id_name_entity_arguments_key
          UNIQUE NULLS NOT DISTINCT (pdp_id, name, entity, arguments)
    );
```
If you want to create the cleanup job by your own, use the following statement:
```sql
SELECT cron.schedule(
    'ttl-cleanup-attributes',
    '* * * * *',
    $$
    WITH deleted AS (
        DELETE FROM attributes
        WHERE expires_at < now()
        RETURNING pdp_id, name, entity, arguments
    )
    SELECT pg_notify(
        'attribute_changes',
        json_build_object(
            'pdpId', pdp_id,
            'name', name,
            'entity', entity,
            'arguments', arguments
        )::text
    )
    FROM deleted
    $$
);
```


## Configuration

1. Include the extension into the `pom.xml` of the PDP. The SAPL Node has it already included.
   ```xml
   <dependency>
     <groupId>io.sapl</groupId>
     <artifactId>attribute-repository-postgres</artifactId>
     <version>4.2.0-SNAPSHOT</version>
   </dependency>
   ```

2. Place an `ext-attributerepository.json` in the folder of the PDP. Examples can be found in the section [Examples](#examples).

    | Field | Required | Default | Description |
    |-------|----------|---------|-------------|
    | `type` | yes | | Must be `postgres` for this extension |
    | `host` | yes | | Hostname of the PostgreSQL server |
    | `port` | yes | | Port of the PostgreSQL server |
    | `username` | yes | | User/Role used by the application |
    | `password` | yes | | Password of the user/role used by the application |
    | `database` | yes | | Database that stores the attributes |
    | `tableName` | no | `attributes` | Table name for the attributes table. It must match the regex pattern`[A-Za-z_][A-Za-z0-9_]*` |

### Examples

#### Extension

A configuration that contains all non-secret fields:

**`ext-attributerepository.json`**
```json
{
  "type": "postgres",
  "host": "localhost",
  "port": 5432,
  "database": "sapl_attributes",
  "tableName": "attributes"
}
```

Secrets should be sealed. You can find more here: [PDP Configuration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/2_2_PDPConfiguration.md).

A cleartext secrets file is only for development use. You need to set `io.sapl.pdp.embedded.secrets.accept-unencrypted` to `true`. Also see: [Spring integration](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-documentation/6_3_Spring.md). A cleartext example is given here:

**`ext-attributerepository-secrets.json`**
```json
{
  "username": "sapl",
  "password": "mySecret"
}
```

A configuration without the table name. The secret file is unchanged. By default the name is `attributes`:

**`ext-attributerepository.json`**
```json
{
  "type": "postgres",
  "host": "localhost",
  "port": 5432,
  "database": "sapl_attributes"
}
```

#### Docker

The official PostgreSQL image doesn't contain the library `pg_cron`. You can build an image with the following Dockerfile:

```dockerfile
FROM postgres:18
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-18-cron \
    && rm -rf /var/lib/apt/lists/*
ENV POSTGRES_DB=sapl_attributes POSTGRES_USER=sapl
COPY init.sql /docker-entrypoint-initdb.d/init.sql
CMD ["postgres", "-c", "shared_preload_libraries=pg_cron", "-c", "cron.database_name=sapl_attributes"]
```

Also create the `init.sql` that's executed once at the first startup:

```sql
CREATE EXTENSION pg_cron;
GRANT USAGE ON SCHEMA cron TO sapl;
```

Now build the image and start the container:

```bash
docker build -t sapl-postgres .
docker run -d --name sapl-postgres -p 5432:5432 -e POSTGRES_PASSWORD=<password> sapl-postgres
```