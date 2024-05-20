# SAPL Eclipse update site 

This module contains an eclipse update site which can be used to install the io.sapl.eclipse.feature into Eclipse.

The following steps can be used to test a locally built version of the plugin in the Eclipse IDE:
- Build the project with `mvn clean install`.
- Open the "Install" dialog in Eclipse (Help -> Install New Software...).
- Add the local update-site  to Eclipse (Click on "Add..." , then on "Local..." and select the local update-site.
  It can be found in this module in the subdirectory [target/repository](target/repository).
- Select the update-site in the dropdown after "Work with". Its name is "SAPL Eclipse update site".
- Select "SAPL" in the table below.
- Click on "Next".
- Click on "Finish".
- Trust all bundles for the sapl plugin in the "Trust Artifacts" dialog. ("Select All" and then "Trust Selected")
- Restart eclipse from the "Software Updates" dialog. ("Restart Now")
- Open a sapl file.

For more information about the term update site see [Eclipse Update Site](https://wiki.eclipse.org/PDE/User_Guide#Update_Site).
