package io.provenance

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Custom gradle plugin to download Provenance, Cosmos, and CosmWasm/wasmd protobuf files.
 *
 */
class DownloadProtosPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register(
            "downloadProtos",
            DownloadProtosTask::class
        ) {
            this.group = "protobuf"
            this.description =
                """Downloads Provenance and Cosmos protobuf files. 
                   Specify the Provenance, Cosmos, and CosmWasm versions: 
                       --provenance-version vX.Y.Z --cosmos-version vX.Y.Z --wasmd-version vX.Y.Z
                   Version information can be found at: 
                       https://github.com/provenance-io/provenance/releases, 
                       https://github.com/cosmos/cosmos-sdk/releases, and 
                       https://github.com/CosmWasm/wasmd/tags."""
        }
    }
}
