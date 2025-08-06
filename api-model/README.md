Explorer API Models
----------

Explorer API models provides the model set used by the Explorer APIs. Instead of having to recreate the models in your 
project, you can import this library to use when calling to the APIs programmatically.

Import
-----

This library is versioned in tandem with the API set.
Available in the [Maven Central repository](https://search.maven.org/artifact/io.provenance.explorer/explorer-api-model).

**BleedingEdge**

Bleeding edge is tagged as `1.0-SNAPSHOT`.

To reach the snapshot, you need to use the snapshot repository
```groovy
repositories {
    maven { url = project.uri("https://central.sonatype.com/repository/maven-snapshots/") }
}
```

and then import as below, with version `1.0-SNAPSHOT`.

**Maven**
```xml
<dependency>
    <groupId>io.provenance.explorer</groupId>
    <artifactId>explorer-api-model</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Gradle Groovy**
```groovy
implementation 'io.provenance.explorer:explorer-api-model:1.0-SNAPSHOT'
```

**Gradle Kotlin**
```kotlin
implementation("io.provenance.explorer:explorer-api-model:1.0-SNAPSHOT")
```

