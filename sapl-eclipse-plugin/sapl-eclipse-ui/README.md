# SAPL Eclipse ui plugin

This module contains a plugin for eclipse which add support for SAPL policy files to the Eclipse IDE.
It provides several features when opening SAPL files:
- syntax highlighting
- content assist
- auto editing
- code formatting
- outline
- folding
- showing syntax errors

For more information about the term Eclipse plugin see [Eclipse Plug-In](https://wiki.eclipse.org/PDE/User_Guide#Plug-in).

## Development of the plugin

It is recommended to use Eclipse with the "M2E - PDE Integration" plugin when developing the SAPL Eclipse ui plugin to
get decent IDE support. The plugin improves the integration between Maven, Eclipse PDE and Tycho. It can be installed
via "Install new Software" from the update-site https://download.eclipse.org/technology/m2e/releases/latest.
