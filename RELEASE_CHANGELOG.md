## [v7.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v7.0.0) - 2025-02-12

## Upgrades
Version 7 of explorer consists of a mass of dependency upgrades that led to a multitude of other cleanups.

**Highlights**
- Java 21
- Kotlin 1.9.25
- Gradle 8
- SpringBoot 3.2.4
- Jakarta EE
- SpringDoc w/ Swagger 3
- Java Time

## Improvements
* Removed materialized view that is unnecessary and causes misdirection when debugging performance issues
* Add a caching interface for block and transaction queries that tend to be used often
* Optimize governance transaction fetching to return results in a timely fashion
* Updated build actions to reduce noise
* Add ktlint to the top level build files
  * Add linter pre-commit hook (Thanks to the Figure Markets team)
* Revamp build files to clean up dependency handling
  * Use libs.versions.toml for dependency declarations
* update to json logging output for better error handling


### Bug Fixes
* Resolve explorer service restarts due to database connection starvation caused by long running governance transaction queries
* Resolve caching issues that caused the internal transaction fetch cache to not be used
* In certain cases spendable balance wasn't able to be fetched
  * query performance improved
* Event processing updates to catch events that were missed since the last Provenance upgrade
