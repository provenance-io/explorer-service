@Library('jenkins-pipeline') _
import com.figure.Common

def common
pipeline {
    agent any
    tools{
        jdk 'JDK12'
    }
    parameters {
        booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: 'Skip docker build & deployment')
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
        stage('Gradle Publish') {
            steps {
                script {
                    gradlePublish("${common.fixedBranchName()}-${env.BUILD_NUMBER}")
                }
            }
        }
        stage('Deployment') {
            when {
                expression { return params.SKIP_DEPLOY != true }
            }
            stages {
                stage('Docker Build') {
                    steps {
                        script {
                            dockerBuild("service", common.dockerTag(), "docker/Dockerfile")
                            if (env.BRANCH_NAME == env.CI_BRANCH) {
                                dockerTag(common.dockerTag, common.dockerLatestTag)
                            }
                        }
                    }
                }
                stage('Docker Push') {
                    steps {
                        script {
                            dockerPush(common.dockerTag())
                            if (env.BRANCH_NAME == env.CI_BRANCH) {
                                dockerPush(common.dockerLatestTag)
                            }
                        }
                    }
                }
                stage('Git Tag') {
                    steps {
                        script {
                            if (env.BRANCH_NAME == "master") {
                                gitTag(this, env.BUILD_NUMBER, env.GIT_COMMIT, env.GIT_URL)
                            }
                        }
                    }
                }
                stage('Apply & Service Image Deploy') {
                    steps {
                        script {
                            if (env.BRANCH_NAME == env.CI_BRANCH) {
                                def env = env.CI_BRANCH == "master" ? "prod" : "test"
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
        }
    }
    post {
        always {
            postNotifySlack(currentBuild.result,'#provenance-builds', ':old_key:')
            script {
                rmrf('build', 'service/build', 'migrations/build')
//                dockerRmi(common.dockerTag(), common.dockerMigrateTag())
            }
        }
    }
}

