rootProject.name = "explorer-service"
include(
    "database",
    "service",
    "api-model",
    "api-client",
)

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.14"
}

gitHooks {
    preCommit {
        from {
            """
                echo "Running pre-commit ktlint format & check"
                stagedFiles=$(git diff --staged --name-only)
                ./gradlew ktlintFormat
                ./gradlew ktlintCheck
                for file in ${'$'}stagedFiles; do
                  if test -f "${'$'}file"; then
                    git add ${'$'}file
                  fi
                done
            """.trimIndent()
        }
    }
    createHooks(true)
}