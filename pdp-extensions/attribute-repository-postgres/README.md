# Postgres Attribute Repository Extension

## Overview

The Postgres Attribute Repository Extension support a PostgreSQL database as an attribute repository. 

## Setup

1. Create a database e.g. `CREATE DATABASE sapl_attributes;`

2. Add the line `cron.database_name = 'sapl_attributes'` to the file `postgresql.conf`. This sets the default name for the cronjob database.

3. Add the extension `shared_preload_libraries = 'pg_cron'` to the file `postgresql.conf`. A restart of Postgres is required to activate the extension. Also add `cron.use_background_workers = on` to `postgresql.conf` if local connections require a password. Otherwise `pg_cron` runs each job over a new connection without a password and the job fails.

4. The database table will be automatically created if missing during startup. If you want to create the table manually, you can use the following statement:

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

5. Activate as a superuser, connected to `sapl_attributes` database the extension for the cronjobs `CREATE EXTENSION pg_cron;`

6. Grant a PostgreSQL role privileges to use the schema `GRANT USAGE ON SCHEMA cron TO <role>;`.

7. If the table is created automatically, the role needs the privilege to create tables in the schema `GRANT CREATE, USAGE ON SCHEMA public TO <role>;`. Since PostgreSQL 15 this privilege is no longer granted by default.

The role used by the application needs neither superuser nor `CREATEDB` privileges. The cleanup job is scheduled with `cron.schedule` and runs as this role in the database `cron.database_name`.


## Configuration

### Examples
