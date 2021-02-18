## Protos

The protos that get built come from the third_party folder.

To populate the folder with the most up to date protos, run `./scripts/copy_proto.sh`.

This does not need to be run very often, but only when you feel the protos should be updated.

In addition, for any new client endpoints, please consider copying in the associated proto from the source of the api.
This will provide you with the exact response object rather than have to create your own. Exceptions do occur where proto
is not used.

* To add new protos to be copied, update `/scripts/copy_proto.sh` with the appropriate pathing. The target location 
must match the package path in the proto file itself.
