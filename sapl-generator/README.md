# SAPL Generator

This project contains configuration files that are used to generate to the SAPL Language project (sapl-lang) and need to be compiled before the language project is compiled.

## SAPLWebIntegrationFragment

This file overwrites the default web configuration file and generates the regex, that is used for highlighting in text editors, in reverse so longer keywords are matched first.

### Example

`permit-unless-deny` and `permit-overrides` must be matched before `permit`, otherwise only the `permit` keyword will be highlighted. 