## Protos

The protos that get built come from the third_party folder.

To populate the folder with the most up to date protos, run `./scripts/copy_proto_v2.sh {prov_version} {cosmos_version}`.

This does not need to be run very often, but only when you feel the protos should be updated.
This script will pull in appropriate protos from the given release version. Please verify that the copied protos are 
correct for your purposes.

To build the protos for use, run `./gradlew proto:build`.
