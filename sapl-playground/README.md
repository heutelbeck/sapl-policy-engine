# SAPL Playground

An interactive web environment for testing and experimenting with SAPL authorization policies. Write policies, define authorization subscriptions, and see real-time decisions with detailed traces. Includes examples, documentation, and shareable permalinks.

## Quick Start

```bash
docker run -d -p 8080:8080 ghcr.io/heutelbeck/sapl-playground:3.1.0-SNAPSHOT
```

Access at http://localhost:8080

## Configuration

### Permalink Base URL

Set the base URL for shareable permalinks using the `SAPL_PLAYGROUND_BASEURL` environment variable:

```bash
docker run -d -p 8080:8080 \
  -e SAPL_PLAYGROUND_BASEURL=https://playground.example.com \
  ghcr.io/heutelbeck/sapl-playground:3.1.0-SNAPSHOT
```

**Format**: Include protocol, no trailing slash (e.g., `http://localhost:8080` or `https://playground.example.com`)

### Docker Compose

```yaml
version: '3.8'
services:
  sapl-playground:
    image: ghcr.io/heutelbeck/sapl-playground:3.1.0-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
      - SAPL_PLAYGROUND_BASEURL=https://playground.example.com
```

## Building from Source

```bash
mvn -U -B clean install -DskipTests
mvn -B spring-boot:build-image -pl sapl-playground -DskipTests -Pproduction
```

## Security Note

This playground is for learning and experimentation. External data sources are blocked. Do not expose on public networks without additional security measures.

## License

Apache License 2.0 - Copyright © 2017-2025 Dominic Heutelbeck