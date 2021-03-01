# Provenance Explorer
The purpose of this api backend is to provide a single API for the Provenance Blockchain Explorer frontend, 
aggregate and cache information needed by the front end (e.g. simplify multiple block/transaction calls into a 
single call), and provide Provenance Blockchain specific endpoints.


## How to run testnet and explorer locally

Necessary tools
- Git
- GO lang
    - comes with Make
- LevelDB
- NPM
- Docker


### To get testnet up
- Clone provenance public repo -> https://github.com/provenance-io/provenance
- Navigate to the repo folder
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

### To get Explorer up
- Clone explorer-service public repo -> https://github.com/provenance-io/explorer-service
- Navigate to the repo folder

#### To get the explorer database up FIRST
- Run `./dc.sh up`

#### To get the explorer API up SECOND
- Go to `service/src/main/resources/application.properties`
- Make sure the following properties are as follows:
    ```
    provenance-explorer.mainnet=false
    provenance-explorer.pb-url=http://localhost:9090
    ```
- Run from command line
    ```
    sh ./gradlew -------------> Installs the gradlew stuff
    ./gradlew build
    ./gradlew bootRun -Dspring.profiles.active=development
    ```

#### To get the explorer UI up THIRD
- Clone explorer-frontend public repo -> https://github.com/provenance-io/explorer-frontend
- Navigate to the repo folder
- Run `npm install`
- Run `npm run local`
- Navigate to http://localhost:3000/explorer


### Swagger URLs
Useful to hit the respective APIs directly

Swagger for Explorer : http://localhost:8612/swagger-ui.html#/ <br/>
Swagger for Testnet: http://localhost:1317/swagger/
