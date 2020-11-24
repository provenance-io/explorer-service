@Library('jenkins-pipeline') _
import com.figure.Common

def common
pipeline {
    agent any
    tools{
        jdk 'JDK11'
    }

    stages {
        stage('Stage Checkout') {
            steps {
                script {
                    common = new Common(this)
                }
                gitCheckout()
            }
        }
        stage('Gradle Build') {
            steps {
                gradleCleanBuild("${common.fixedBranchName()}-${env.BUILD_NUMBER}")
            }
        }
        stage('Docker Build') {
            steps {
                script {
                    dockerBuild("service", common.dockerTag(), "docker/Dockerfile")
                    if (env.BRANCH_NAME == env.CI_BRANCH || env.BRANCH_NAME == "main") {
                        dockerTag(common.dockerTag, common.dockerLatestTag)
                    }
                }
            }
        }
        stage('Docker Push') {
            steps {
                script {
                    dockerPush(common.dockerTag())
                    if (env.BRANCH_NAME == env.CI_BRANCH || env.BRANCH_NAME == "main") {
                        dockerPush(common.dockerLatestTag)
                    }
                }
            }
        }
        stage('Git Tag') {
            steps {
                script {
                    if (env.BRANCH_NAME == "main") {
                        gitTag(this, env.BUILD_NUMBER, env.GIT_COMMIT, env.GIT_URL)
                    }
                }
            }
        }
        stage('Apply & Service Image Deploy') {
            steps {
                script {
                    if (env.BRANCH_NAME == env.CI_BRANCH || env.BRANCH_NAME == "main") {
                        def env = env.CI_BRANCH == "main" ? "prod" : "test"
                        provenanceApplyDeploy() //kubectl apply -f deployment.yaml
                        provenancePatchDeploy("apis", "${common.repoName}",
                                [[ op: "replace",
                                   path: "/spec/template/spec/deployment/image/${env}",
                                   value: "${common.dockerTag}"]],
                                "pd", "json")
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if(currentBuild.result.contains("SUCCESS")) {
                    postNotifySlack(currentBuild.result, '#provenance-builds', ':blockyapproves:')
                } else if(currentBuild.result.contains("FAILURE")) {
                    postNotifySlack(currentBuild.result, '#provenance-builds', ':dumpsterfire:')
                } else if(currentBuild.result.contains("ABORTED")) {
                    postNotifySlack(currentBuild.result, '#provenance-builds', ':_homer_:')
                } else {
                    postNotifySlack(currentBuild.result, '#provenance-builds', ':surprised_pikachu:')
                }
            }
            script {
                rmrf('build', 'service/build')
                dockerRmi(common.dockerTag())
            }
        }
    }
}