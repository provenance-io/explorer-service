import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

sourceSets.main {
    proto.srcDirs("../third_party/proto/")
}

dependencies {
//    protobuf(files("cosmWasm-v0.17.0.tar.gz"))
    api(Libraries.ProtobufJavaUtil)
    implementation(Libraries.ProtobufKotlin)
    api(Libraries.GrpcKotlinStub)
    api(Libraries.GrpcProtobuf)
    implementation(Libraries.GrpcStub)

    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        api("javax.annotation:javax.annotation-api:1.3.1")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = Libraries.ProtocArtifact
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id(PluginIds.Grpc) {
            artifact = Libraries.GrpcArtifact
        }
        id(PluginIds.GrpcKt) {
            artifact = Libraries.GrpcKotlinArtifact
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id(PluginIds.Grpc)
                id(PluginIds.GrpcKt)
            }
            it.builtins {
                id(PluginIds.Kotlin)
            }
        }
    }
}

tasks.register<io.provenance.DownloadProtosTask>("downloadProtos") {
    provenanceVersion = Versions.Provenance
    cosmosVersion = Versions.Cosmos
    wasmdVersion = Versions.Wasmd
    ibcVersion = Versions.Ibc
}

//tasks.register("downloadTest"){
//    mapOf("cosmWasm-v0.17.0.tar.gz" to "https://github.com/CosmWasm/wasmd/tarball/v0.17.0")
//        .forEach { (k, v) -> download(v,k) }
//}
//
//fun download(url : String, path : String){
//    val destFile = File(path)
//    ant.invokeMethod("get", mapOf("src" to url, "dest" to destFile))
//}
