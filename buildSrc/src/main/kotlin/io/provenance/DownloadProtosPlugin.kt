package io.provenance

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Custom gradle plugin to download Provenance and Cosmos protobuf files.
 *
 */
class DownloadProtosPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register(
            "downloadProtos",
            DownloadProtosTask::class
        ) {
            this.group = "protobuf"
            this.description =
                "Downloads Provenance and Cosmos protobuf files. " +
                " Specify the Provenance and Cosmos versions: --provenance-version vX.Y.Z --cosmos-version vX.Y.Z." +
                " Version information can be found at https://github.com/provenance-io/provenance/releases and " +
                " https://github.com/cosmos/cosmos-sdk/releases."
        }
    }
}
