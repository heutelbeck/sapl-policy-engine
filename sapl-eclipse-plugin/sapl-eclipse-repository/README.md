# SAPL Eclipse Update Site

P2 repository for distributing the SAPL Eclipse plugin.

## Installing from Local Build

1. Build the project:
   ```shell
   mvn clean install
   ```

2. In Eclipse, open **Help** > **Install New Software...**

3. Click **Add...**, then **Local...** and select `target/repository` in this module

4. Select the update site in the "Work with" dropdown (named "SAPL Eclipse update site")

5. Check **SAPL** in the feature list

6. Click **Next**, then **Finish**

7. In the "Trust Artifacts" dialog, click **Select All**, then **Trust Selected**

8. Click **Restart Now** when prompted

9. Open a `.sapl` file to verify the plugin is working

For more information about Eclipse update sites, see the
[Eclipse PDE User Guide](https://wiki.eclipse.org/PDE/User_Guide#Update_Site).
