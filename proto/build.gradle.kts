import com.google.protobuf.gradle.*

sourceSets.main {
    proto.srcDirs("../third_party/proto/")
    java.srcDirs("build/generated/source/proto/main/java")
}

dependencies {
    api(Libraries.ProtobufJava)
    api(Libraries.GrpcStub)
    api(Libraries.GrpcProtobuf)

    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        api("javax.annotation:javax.annotation-api:1.3.1")
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
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without options.
                id(PluginIds.Grpc)
            }
        }
    }
}

tasks.register<io.provenance.DownloadProtosTask>("downloadProtos") {
    provenanceVersion = Versions.Provenance
    cosmosVersion = Versions.Cosmos
}
