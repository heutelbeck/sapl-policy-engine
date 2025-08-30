## v3.0.0 - 2025-07-28

### Added
- Tracing capabilities for improved auditability
- Interceptors for enhanced control over policy enforcement
- Clearer and more informative error messages
- PIP / PEP integrations:
  - JWT PIP
  - HTTP PIP
  - Spring Data PEPs

### Changed
- Core systems extensively refactored and re-engineered
- Upgraded to Spring Boot 3 (breaking change potential)

### Upgrade Notes
- Major rewrite from v2.0.0 — embedded PDP/PEP APIs may have breaking changes. Running a dry-run upgrade test is strongly recommended. Reach out via GitHub or Discord if unsure.

**Full Changelog**: https://github.com/heutelbeck/sapl-policy-engine/compare/v2.0.0...v3.0.0

## v2.0.1 — 2021-12-27

### Fixed
- Corrected evaluation issues with multi-decision requests
- Resolved null pointer exceptions in specific attribute finder scenarios
- Bug fix in policy enforcement when using asynchronous data sources

### Changed
- Minor refactorings for improved stability

### Upgrade Notes
- No breaking changes compared to v2.0.0

**Full Changelog**: https://github.com/heutelbeck/sapl-policy-engine/compare/v2.0.0...v2.0.1

## v2.0.0 — 2021-12-01

This constitutes the first public release of the SAPL Engine.

### Added
- Full rewrite of the SAPL Policy Engine core to support reactive programming (Project Reactor)
- Integration with Spring Boot 2.x for easier embedding into applications
- First-class support for Policy Information Points (PIPs) and Policy Enforcement Points (PEPs)

