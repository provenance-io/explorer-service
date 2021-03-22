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
            this.group = "help"
            this.description = "Downloads Provenance and Cosmos protobuf files.  Specify the Provenance and Cosmos versions: --provenance-version v0.2.1 --cosmos-version v0.42.2"
        }
    }
}
