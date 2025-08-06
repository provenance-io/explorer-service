Explorer API Client
----------

Explorer API client provides the clients to call into the Explorer APIs. Instead of having to manually set up calls to 
the APIs, you can import this library to use when accessing to the APIs programmatically.

This is bundled with the appropriate API model library, so you dont have to import the model library separately for use. 

Import
-----

This library is versioned in tandem with the API set.
Available in the [Maven Central repository](https://search.maven.org/artifact/io.provenance.explorer/explorer-api-client).

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
    <artifactId>explorer-api-client</artifactId>
    <version>latest</version>
</dependency>
```

**Gradle Groovy**
```groovy
implementation 'io.provenance.explorer:explorer-api-client:latest'
```

**Gradle Kotlin**
```kotlin
implementation("io.provenance.explorer:explorer-api-client:latest")
```

Usage
----

To use the clients, you need to define the base url you want to hit. Examples include:
* `https://service-explorer.test.figure.tech`
* `https://service-explorer.figure.tech`

Using the defined url, you would instantiate the Client as 
```kotlin
fun main(args: Array<String>) {
    val url = "https://service-explorer.test.figure.tech"
    
    // This gives you the setup to then call into different client sets
    val baseClient = ExplorerClient(url) 
}
```

Then you can create different clients depending on the API set you want to access
```kotlin
fun main(args: Array<String>) {
    val url = "https://service-explorer.test.figure.tech"
    
    // This gives you the setup to then call into different client sets
    val baseClient = ExplorerClient(url) 
    
    // This gives you the block client, where you'd access the Block API set
    val blockApi = baseClient.blockClient
}
```

From there, you can access any of the client's apis, and pass the necessary information.
```kotlin
fun main(args: Array<String>) {
    val url = "https://service-explorer.test.figure.tech"
    
    // This gives you the setup to then call into different client sets
    val baseClient = ExplorerClient(url) 
    
    // This gives you the block client, where you'd access the Block API set
    val blockApi = baseClient.blockClient
    
    // Examples of accessing the apis via the client
    val currBlock: BlockSummary = blockApi.currentHeight()
    val height: BlockSummary = blockApi.atHeight(12108454)
}
```
