
<div align="center">
<img src="./docs/logo.svg" alt="Provenance Explorer Service"/>
</div>
<br/><br/>

# Provenance Explorer
The purpose of this api backend is to provide a single API for the [Provenance Blockchain][Provenance] Explorer frontend, 
aggregate and cache information needed by the front end (e.g. simplify multiple block/transaction calls into a 
single call), and provide Provenance Blockchain specific endpoints.

## Status

[![Latest Release][release-badge]][release-latest]
[![Apache 2.0 License][license-badge]][license-url]
[![LOC][loc-badge]][loc-report]
[![Lint Status][lint-badge]][lint-report]

[license-badge]: https://img.shields.io/github/license/provenance-io/explorer-service.svg
[license-url]: https://github.com/provenance-io/explorer-service/blob/main/LICENSE
[release-badge]: https://img.shields.io/github/tag/provenance-io/explorer-service.svg
[release-latest]: https://github.com/provenance-io/explorer-service/releases/latest
[loc-badge]: https://tokei.rs/b1/github/provenance-io/explorer-service
[loc-report]: https://github.com/provenance-io/explorer-service
[lint-badge]: https://github.com/provenance-io/explorer-service/workflows/ktlint/badge.svg
[lint-report]: https://github.com/provenance-io/explorer-service/actions/workflows/ktlint.yml
[provenance]: https://provenance.io/#overview

### Table of Contents
- [Provenance Explorer](#provenance-explorer)
    * [How to run testnet and explorer locally](#how-to-run-testnet-and-explorer-locally)
        + [To get testnet up](#to-get-testnet-up)
            - [To start a local isolated cluster](#to-start-a-local-isolated-cluster)
            - [To start a node on the public chain](#to-start-a-node-on-the-public-chain)
        + [To get Explorer up](#to-get-explorer-up)
            - [Manually - in terminals](#manually---in-terminals)
                + [To get the explorer database up FIRST](#to-get-the-explorer-database-up-first)
                + [To get the explorer API up SECOND](#to-get-the-explorer-api-up-second)
                + [To get the explorer UI up THIRD](#to-get-the-explorer-ui-up-third)
            - [Via Docker and Docker-compose](#via-docker-and-docker-compose)
        + [Swagger URLs](#swagger-urls)
        + [Linting](#linting)

## How to run testnet and explorer locally

Necessary tools
- Git
- GO lang
    - comes with Make
- LevelDB
- NPM
- Docker
- Docker-compose


### To get testnet up
- Clone provenance public repo -> https://github.com/provenance-io/provenance
- Navigate to the repo folder

#### To start a local isolated cluster
- Run `make clean ; make build; make localnet-start`
	- This allows you to stand up 4 nodes in a local cluster
</br></br>
- If you want to enable the swagger
    - go to `build/node0/config/app.toml`
    - edit the file at 
        ```
        # Swagger defines if swagger documentation should automatically be registered.
        swagger = false <- set to true
        ```
	- run `make localnet-start`
</br></br>
- To stop the cluster, run `make localnet-stop`

#### To start a node on the public chain
- Run `git fetch --all; git checkout {tag_you_want}; make clean install;`
- TODO: Fill in the rest

### To get Explorer up
- Clone explorer-service public repo -> https://github.com/provenance-io/explorer-service
- Navigate to the repo folder

#### Manually - in terminals

###### To get the explorer database up FIRST
- Run `./scripts/dc.sh up`
    - To get an existing database up again, run `./scripts/dc.sh up-cached`

###### To get the explorer API up SECOND
- Go to `service/src/main/resources/application.properties`
- Make sure the following properties are as follows:
    ```
    explorer.mainnet=false
    explorer.pb-url=http://localhost:9090
    ```
- Run from command line
    ```
    sh ./gradlew -------------> Installs the gradlew stuff
    ./gradlew clean
    ./gradlew build
    ./gradlew bootRun -Dspring.profiles.active=development
    ```

###### To get the explorer UI up THIRD
- Clone explorer-frontend public repo -> https://github.com/provenance-io/explorer-frontend
- Navigate to the repo folder
- Run `npm install`
- Run `npm run local`
- Navigate to http://localhost:3000/explorer

#### Via Docker and Docker-compose
- Run `docker-compose -f docker/docker-compose.yml up`
- This stands up dockers for database, service and frontend.

### Swagger URLs
Useful to hit the respective APIs directly

Swagger for Explorer : http://localhost:8612/swagger-ui/index.html <br/>
Swagger for Testnet: http://localhost:1317/swagger/

### Linting
We use Ktlint for our linting use -> https://pinterest.github.io/ktlint/rules/standard/
Currently on version `0.47.1`

To install the Kotlin linter run:
```
brew install ktlint
```

In order to automatically lint/check for things that can't be autocorrected run:
```
ktlint -F "**/*.kt" --disabled_rules=filename,chain-wrapping,enum-entry-name-case,multiline-if-else
```
This will also correct linting issues, and you can add and commit the updates.

There is a GHA that checks for linting issues, and produces a report on the associated PR.
