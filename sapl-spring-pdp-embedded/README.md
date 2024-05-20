# Embedded SAPL Policy Decision Point (PDP) for Spring Boot

This module provides an embedded PDP which is capable of making authorization decisions based on rules and policies expressed in the Streaming Attribute Policy Language stored in application resources or on the file system.

## Configuration

The embedded PDP is configured with the following properties stored in an ```application.yml``` or ```application.properties``` file.

### io.sapl.pdp.embedded.pdpConfigType

This property selects the source of configuration and policies. The options are:

* ```RESOURCES```: Loads a fixed set of documents and pdp.json from the bundled resource. 
  These will be loaded once and cannot be updated at runtime of the system.
* ```FILESYSTEM```: Monitors directories for documents and configuration. Will
  automatically update any changes made to the documents and configuration at
  runtime. Changes will directly be reflected in the decisions made in already
  existing subscriptions and send new decisions if applicable.

Default value: ```RESOURCES```

### io.sapl.pdp.embedded.pdpConfigType

This property selects the indexing algorithm used by the PDP. The options are:

* ```NAIVE```: A simple implementation for systems with small numbers of documents.

* ```CANONICAL```: An improved index for systems with large numbers of documents.
  Takes more time to update and initialize but significantly reduces retrieval
  time.

Default value: ```NAIVE```

### io.sapl.pdp.embedded.configPath

This property sets the path to the folder where the pdp.json configuration file is located.
If the pdpConfigType is set to ```RESOURCES```, the path is relative to the root of the context path.
For ```FILESYSTEM`` , it must be a valid path on the system's file system.

Default value: ```"/policies"```

### io.sapl.pdp.embedded.configPath
This property sets the path to the folder where the *.sapl documents are
located. If the pdpConfigType is set to ```RESOURCES```, the path is relative to the root of the context path.
For ```FILESYSTEM```, it must be a valid path on the system's file system.

Default value: ```"/policies"```

### io.sapl.pdp.embedded.prettyPrintReports

If this property is set to true, JSON in logged traces and reports are pretty printed.

Default value: ```false```

### io.sapl.pdp.embedded.printTrace

If this property is set to true, the full JSON evaluation trace is logged on
each decision.

Default value: ```false```

### io.sapl.pdp.embedded.printTrace

If this property is set to true, the JSON evaluation report is logged on each
decision.

Default value: ```false```

### io.sapl.pdp.embedded.printTextReport

If this property is set to true, the textual decision report is logged on
each decision.

Default value: ```false```
