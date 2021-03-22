package io.provenance

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.LaxRedirectStrategy


open class DownloadProtosTask : DefaultTask() {
    private val tempPrefix = this.javaClass.name

    @Option(
        option = "provenance-version",
        description = "Provenance release version (e.g. v0.2.1)"
    )
    @Input
    var provenanceVersion: String? = null

    @Option(
        option = "cosmos-version",
        description = "Cosmos release version (e.g. 0.42)"
    )
    @Input
    var cosmosVersion: String? = null


    @TaskAction
    fun downloadProtos() {

        cleanDestination(thirdPartyPath())

        unzip(
            file = toTempFile("https://github.com/provenance-io/provenance/releases/download/${this.provenanceVersion}/protos-${this.provenanceVersion}.zip"),
            destinationDir = thirdPartyPath(),
            includePattern = Regex(".*\\.proto\$")
        )

        untar(
            file = unGzip(toTempFile("https://github.com/cosmos/cosmos-sdk/tarball/${this.cosmosVersion}")),
            destinationDir = thirdPartyPath(),
            includePattern = Regex(".*/proto/.*\\.proto\$"),
            excludePattern = Regex(".*testutil/.*|.*proto/ibc/.*"),
            protoRootDir = "proto"
        )
    }

    private fun toTempFile(url: String): File =
        HttpClients.custom().setRedirectStrategy(LaxRedirectStrategy()).build()
            .use { client ->
                client.execute(
                    HttpGet(url)
                ) { response ->
                    if (response == null || response.statusLine.statusCode != 200) {
                        throw IOException("could not retrieve: ${response?.statusLine?.reasonPhrase}")
                    }
                    File.createTempFile(tempPrefix, "zip").let { tempFile ->
                        IOUtils.copy(
                            response.entity.content,
                            FileOutputStream(
                                tempFile
                            )
                        )
                        tempFile
                    }
                }
            }

    private fun cleanDestination(destinationDir: String) {
        FileUtils.forceDelete(File(destinationDir))
        FileUtils.forceMkdir(File(destinationDir))
    }

    private fun unzip(
        file: File,
        destinationDir: String,
        includePattern: Regex
    ) {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .forEach { zipEntry ->
                    handleZipEntry(
                        zipInputStream = zip.getInputStream(zipEntry),
                        zipEntry = zipEntry,
                        destinationDir = File(destinationDir),
                        includePattern = includePattern
                    )
                }
        }
    }

    private fun thirdPartyPath() = "${this.project.rootProject.rootDir}${File.separator}third_party"

    @Throws(IOException::class)
    private fun handleZipEntry(
        zipInputStream: InputStream,
        zipEntry: ZipEntry,
        destinationDir: File,
        includePattern: Regex
    ) {
        if (zipEntry.name.matches(includePattern)) {
            val newFile = File(destinationDir, zipEntry.name)
            if (zipEntry.isDirectory) {
                if (!newFile.isDirectory && !newFile.mkdirs()) {
                    throw IOException("Failed to create directory $newFile")
                }
            } else {
                // fix for Windows-created archives
                val parent = newFile.parentFile
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory $parent")
                }
                IOUtils.copy(zipInputStream, FileOutputStream(newFile))
            }
        }
    }

    @Throws(IOException::class)
    private fun untar(
        file: File,
        destinationDir: String,
        includePattern: Regex,
        excludePattern: Regex,
        protoRootDir: String
    ) {
        val tempDir = File.createTempFile(tempPrefix, "dir").parentFile

        var topTarDirectory: File? = null

        TarArchiveInputStream(FileInputStream(file)).use { tarArchiveInputStream ->
            var tarEntry: TarArchiveEntry?
            while (tarArchiveInputStream.nextTarEntry.also {
                    tarEntry = it
                } != null) {
                if (topTarDirectory == null) {
                    topTarDirectory = File(tempDir.absolutePath + File.separator + tarEntry?.name)
                }

                if (tarEntry?.name?.matches(includePattern) == true &&
                    tarEntry?.name?.matches(excludePattern) == false
                ) {
                    //write to temp file first so we can pick the dirs we want
                    val outputFile = File(tempDir.absolutePath + File.separator + tarEntry?.name)
                    if (tarEntry?.isDirectory == true) {
                        if (!outputFile.exists()) {
                            outputFile.mkdirs()
                        }
                    } else {
                        outputFile.let {
                            it.parentFile.mkdirs()
                            IOUtils.copy(
                                tarArchiveInputStream,
                                FileOutputStream(it)
                            )
                        }
                    }
                }
            }
        }
        //Copy from proto root dir to the local project third_party dir
        topTarDirectory?.let { topTar ->
            mutableListOf<File>().let { matchedDirs ->
                findDirectory(topTar, protoRootDir,matchedDirs)
                matchedDirs.forEach {
                    FileUtils.copyDirectory(it, File("$destinationDir${File.separator}proto"))
                }
            }
        }?: throw IOException("tar file ${file.absolutePath} is not a well formed tar file - missing top level directory")
    }

    private fun findDirectory(
        currentDirectory: File,
        findDirectory: String,
        matchingDirectories: MutableList<File>
    ) {
        if (currentDirectory.isDirectory && currentDirectory.name == findDirectory) {
            matchingDirectories.add(currentDirectory)
        } else {
            val files = currentDirectory.listFiles() ?: emptyArray()
            for (file in files) {
                if (file.isFile) {
                    continue
                }
                if (file.isDirectory) {
                    findDirectory(file, findDirectory, matchingDirectories)
                }
            }
        }
    }


    @Throws(IOException::class)
    private fun unGzip(gZippedFile: File): File =
        GZIPInputStream(FileInputStream(gZippedFile)).let {
            File.createTempFile(tempPrefix, "tar").let { tempFile ->
                val fos = FileOutputStream(tempFile)
                val buffer = ByteArray(1024)
                var len: Int
                while (it.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
                it.close()
                tempFile
            }
        }

}
