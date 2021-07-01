# Provenance Blockchain Protobuf Install and Build

The Provenance Explorer uses a combination of [Provenance](https://github.com/provenance-io/provenance), 
[Cosmos](https://github.com/cosmos/cosmos-sdk), and [CosmWasm/wasmd](https://github.com/CosmWasm/wasmd) 
[protobuf](https://developers.google.com/protocol-buffers) definitions.
Protocol buffers (protobuf) are Google's language-neutral, platform-neutral, 
extensible mechanism for serializing structured data.  The Provenance
[gRPC](https://grpc.io) and protobuf provide the RPC mechanism that Provenance 
Explorer (and all middleware, really) uses to communicate with the Provenance blockchain.

## Download Provenance Blockchain Protos

This `proto` module compiles the protobuf definitions (protos) from the `third_party` directory.
The compiled protos are then used in the Provenance Explorer `service` module
to communicate with the blockchain.

Before compiling the protos, they must be downloaded locally.  The `third_party`
directory contains the last download of the protos.  To update the `third_party`
directory run this `gradle` task *from the root project directory*:

```bash
./gradlew proto:downloadProtos
```

> The `downloadProtos` task will clean the `third_party` directory prior to
> download.  Do not edit the protos in that directory.

This `gradle` task will download the Provenance, Cosmos, and CosmWasm/wasmd proto versions defined
in the `./buildSrc/src/main/kotlin/Dependencies.kt` file:

```kotlin
    //external protos
    const val Provenance = "v1.5.0"
    const val Cosmos = "v0.42.6"
    const val Wasmd = "v0.17.0"
```

To manually specify the versions run this `gradle` task  *from the root project directory*:

```bash
./gradlew downloadProtos --provenance-version v1.5.0 --cosmos-version v0.42.6 --wasmd-version v0.17.0
```

> The proto download process does not need to be run very often, 
> only when major version of the Provenance or Cosmos proto definitions
> are released.

## Build Protos

Once the protos have been downloaded, run the `gradle` task *from the root project directory*:

```bash
./gradlew clean proto:build
```
