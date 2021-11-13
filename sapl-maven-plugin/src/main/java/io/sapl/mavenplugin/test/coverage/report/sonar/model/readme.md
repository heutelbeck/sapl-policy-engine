# Generate Models from Sonar Generic Coverage XSD Schema

1. Get schema from here <https://docs.sonarqube.org/latest/analysis/generic-test/>

2. Modify sonar-generic-coverage.xsd to bound schema:
```
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <xs:schema version="1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
```

3. Generate via `"xjc -d sonar -p io.sapl.test.mavenplugin.model.sonar sonar-generic-coverage.xsd"`