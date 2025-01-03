# Eclipse Settings

The default development environment for SAPL is Eclipse. The reason for this is, that the core of SAPL is based on XText, which is an Eclipse project itself and has matching tooling for Eclipse.

This document outlines some key configurations to make in your Eclipse install to have the best development experience.

## Code Formatter and Warnings

SAPL applies a standardized code formatting style based on the Eclipse auto formatter. Import the settings from the [SAPL Formatter setting XML](https://github.com/heutelbeck/sapl-policy-engine/blob/master/sapl-code-style/src/main/resources/eclipse/formatter.xml) file. To import it go to "Window -> Preferences". Then navigate to "Java -> Code Style -> Formatter" and use "Import..." to import the settings.

## Mandatory Eclipse Plug-Ins 

The following Plug-Ins are mandatory to have installed in order to be able to work with the SAPL source code without a lot of errors within the IDE.

### Project Lombok integration.

SAPL makes extensive use of Lombok to eliminate boilerplate code. Without the Lombok Plug-in installed, that methods generated by Lombok will not be visible to Eclipse and the project will be littered with errors within the IDE, even when the code is correct and fully functional.

The Lombok Plug-in cannot be downloaded via the Eclipse Marketplace. Please download it from the official [Project Lombok download page](https://projectlombok.org/download).

### PDE Integration

SAPL generates an Eclipse Plug-in. This requires the tycho tooling for building and packaging. Eclipse itself cannot deal with this out of the box.
Install the "M2E - PDE Integration" from the update site [Eclipse IDE integration for Maven](https://download.eclipse.org/technology/m2e/releases/latest). Use the "Help -> Install New Software..." menu in Eclipse. The update site should already be present.

Further, in the "Install New Software..." add the update site (https://download.eclipse.org/eclipse/updates/4.4) and install "Eclipse PDE Plug-in Developer Resources".

### Xtext Eclipse Plug-In

To get support for editing the Xtext grammar files, install the "Eclipse Xtext" plug-in. Use the "Help -> Eclipse Marketplace..." menu and search for "Xtext".

## Optional Plug-Ins

### Spring Tools 4

To get quality of life improvements for the Spring components install "Spring Tools 4" from "Help -> Eclipse Marketplace...".

### SonarQube for IDE

To identify issues which would surface in SonarCloud as well early before pushing, install "SonarQube for IDE" from "Help -> Eclipse Marketplace...".

### SpotBugs

To identify issues which would surface in SonarCloud as well early before pushing, install "SpotBugs Eclipse plugin" from "Help -> Eclipse Marketplace...".
