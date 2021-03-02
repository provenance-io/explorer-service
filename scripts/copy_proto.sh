#!/usr/bin/env bash

echo "Starting proto copy"

echo "Removing existing protos"
rm -r third_party

PROVENANCE_URL="https://raw.githubusercontent.com/provenance-io/provenance/main/proto/provenance"
PROVENANCE_MAIN_URL="https://raw.githubusercontent.com/provenance-io/provenance/main"

COSMOS_URL="https://raw.githubusercontent.com/cosmos/cosmos-sdk/master/proto/cosmos"
TM_URL="https://raw.githubusercontent.com/tendermint/tendermint/v0.34.x/proto/tendermint"

PROVENANCE_TYPES="third_party/proto/provenance"
GOOGLE_TYPES="third_party/proto/google"
TM_CRYPTO_TYPES="third_party/proto/tendermint/crypto"
TM_ABCI_TYPES="third_party/proto/tendermint/abci"
TM_TYPES="third_party/proto/tendermint/types"
TM_P2P="third_party/proto/tendermint/p2p"
TM_VERSION="third_party/proto/tendermint/version"
TM_LIBS="third_party/proto/tendermint/libs/bits"
GOGO_PROTO_TYPES="third_party/proto/gogoproto"
COSMOS_PROTO_TYPES="third_party/proto/cosmos_proto"
COSMOS_BASE_TYPES="third_party/proto/cosmos/base"
COSMOS_DISTRIBUTION_TYPES="third_party/proto/cosmos/distribution"
COSMOS_SIGNING_TYPES="third_party/proto/cosmos/tx/signing"
COSMOS_STAKING_TYPES="third_party/proto/cosmos/staking"
COSMOS_GOV_TYPES="third_party/proto/cosmos/gov"
COSMOS_SLASHING_TYPES="third_party/proto/cosmos/slashing"
COSMOS_CRYPTO_TYPES="third_party/proto/cosmos/crypto"
COSMOS_AUTH_TYPES="third_party/proto/cosmos/auth"
COSMOS_BANK_TYPES="third_party/proto/cosmos/bank"
COSMOS_TX_TYPES="third_party/proto/cosmos/tx"
COSMOS_PARAM_TYPES="third_party/proto/cosmos/params"
CONFIO_TYPES="third_party/proto/confio"


echo "Adding confio"
mkdir -p $CONFIO_TYPES
curl -sSL $PROVENANCE_MAIN_URL/$CONFIO_TYPES/proofs.proto >$CONFIO_TYPES/proofs.proto

echo "Adding cosmos_proto"
mkdir -p $COSMOS_PROTO_TYPES
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_PROTO_TYPES/cosmos.proto >$COSMOS_PROTO_TYPES/cosmos.proto

echo "Adding cos suth"
mkdir -p $COSMOS_AUTH_TYPES/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_AUTH_TYPES/v1beta1/auth.proto >$COSMOS_AUTH_TYPES/v1beta1/auth.proto
curl -sSL $COSMOS_URL/auth/v1beta1/query.proto >$COSMOS_AUTH_TYPES/v1beta1/query.proto

echo "Adding cos bank"
mkdir -p $COSMOS_BANK_TYPES/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_BANK_TYPES/v1beta1/bank.proto >$COSMOS_BANK_TYPES/v1beta1/bank.proto
curl -sSL $COSMOS_URL/bank/v1beta1/query.proto >$COSMOS_BANK_TYPES/v1beta1/query.proto
curl -sSL $COSMOS_URL/bank/v1beta1/tx.proto >$COSMOS_BANK_TYPES/v1beta1/tx.proto

echo "Adding cos base"
mkdir -p $COSMOS_BASE_TYPES/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_BASE_TYPES/v1beta1/coin.proto >$COSMOS_BASE_TYPES/v1beta1/coin.proto

echo "Adding cos base/abci"
mkdir -p $COSMOS_BASE_TYPES/abci/v1beta1
curl -sSL $COSMOS_URL/base/abci/v1beta1/abci.proto >$COSMOS_BASE_TYPES/abci/v1beta1/abci.proto

echo "Adding cos base/query"
mkdir -p $COSMOS_BASE_TYPES/query/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_BASE_TYPES/query/v1beta1/pagination.proto >$COSMOS_BASE_TYPES/query/v1beta1/pagination.proto

echo "Adding cos base/reflection"
mkdir -p $COSMOS_BASE_TYPES/reflection/v1beta1
curl -sSL $COSMOS_URL/base/reflection/v1beta1/reflection.proto >$COSMOS_BASE_TYPES/reflection/v1beta1/reflection.proto

echo "Adding cos base/tendermint"
mkdir -p $COSMOS_BASE_TYPES/tendermint/v1beta1
curl -sSL $COSMOS_URL/base/tendermint/v1beta1/query.proto >$COSMOS_BASE_TYPES/tendermint/v1beta1/query.proto

echo "Adding cos crypto/multisig"
mkdir -p $COSMOS_CRYPTO_TYPES/multisig/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_CRYPTO_TYPES/multisig/v1beta1/multisig.proto >$COSMOS_CRYPTO_TYPES/multisig/v1beta1/multisig.proto
curl -sSL $COSMOS_URL/crypto/multisig/keys.proto >$COSMOS_CRYPTO_TYPES/multisig/keys.proto

echo "Adding cos crypto/secp256k1"
mkdir -p $COSMOS_CRYPTO_TYPES/secp256k1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_CRYPTO_TYPES/secp256k1/keys.proto >$COSMOS_CRYPTO_TYPES/secp256k1/keys.proto

echo "Adding cos crypto/ed25519"
mkdir -p $COSMOS_CRYPTO_TYPES/ed25519
curl -sSL $COSMOS_URL/crypto/ed25519/keys.proto >$COSMOS_CRYPTO_TYPES/ed25519/keys.proto

echo "Adding cos distribution"
mkdir -p $COSMOS_DISTRIBUTION_TYPES/v1beta1
curl -sSL $COSMOS_URL/distribution/v1beta1/query.proto >$COSMOS_DISTRIBUTION_TYPES/v1beta1/query.proto
curl -sSL $COSMOS_URL/distribution/v1beta1/distribution.proto >$COSMOS_DISTRIBUTION_TYPES/v1beta1/distribution.proto

echo "Adding cos gov"
mkdir -p $COSMOS_GOV_TYPES/v1beta1
curl -sSL $COSMOS_URL/gov/v1beta1/tx.proto >$COSMOS_GOV_TYPES/v1beta1/tx.proto
curl -sSL $COSMOS_URL/gov/v1beta1/gov.proto >$COSMOS_GOV_TYPES/v1beta1/gov.proto

echo "Adding cos params"
mkdir -p $COSMOS_PARAM_TYPES/v1beta1
curl -sSL $COSMOS_URL/params/v1beta1/params.proto >$COSMOS_PARAM_TYPES/v1beta1/params.proto

echo "Adding cos signing"
mkdir -p $COSMOS_SIGNING_TYPES/v1beta1
curl -sSL $PROVENANCE_MAIN_URL/$COSMOS_SIGNING_TYPES/v1beta1/signing.proto >$COSMOS_SIGNING_TYPES/v1beta1/signing.proto

echo "Adding cos slashing"
mkdir -p $COSMOS_SLASHING_TYPES/v1beta1
curl -sSL $COSMOS_URL/slashing/v1beta1/query.proto >$COSMOS_SLASHING_TYPES/v1beta1/query.proto
curl -sSL $COSMOS_URL/slashing/v1beta1/slashing.proto >$COSMOS_SLASHING_TYPES/v1beta1/slashing.proto

echo "Adding cos staking"
mkdir -p $COSMOS_STAKING_TYPES/v1beta1
curl -sSL $COSMOS_URL/staking/v1beta1/query.proto >$COSMOS_STAKING_TYPES/v1beta1/query.proto
curl -sSL $COSMOS_URL/staking/v1beta1/staking.proto >$COSMOS_STAKING_TYPES/v1beta1/staking.proto

echo "Adding cos tx"
mkdir -p $COSMOS_TX_TYPES/v1beta1
curl -sSL $COSMOS_URL/tx/v1beta1/service.proto >$COSMOS_TX_TYPES/v1beta1/service.proto
curl -sSL $COSMOS_URL/tx/v1beta1/tx.proto >$COSMOS_TX_TYPES/v1beta1/tx.proto

echo "Adding gogo"
mkdir -p "$GOGO_PROTO_TYPES"
curl -sSL $PROVENANCE_MAIN_URL/$GOGO_PROTO_TYPES/gogo.proto >$GOGO_PROTO_TYPES/gogo.proto

echo "Adding google/api"
mkdir -p $GOOGLE_TYPES/api
curl -sSL $PROVENANCE_MAIN_URL/$GOOGLE_TYPES/api/annotations.proto >$GOOGLE_TYPES/api/annotations.proto
curl -sSL $PROVENANCE_MAIN_URL/$GOOGLE_TYPES/api/http.proto >$GOOGLE_TYPES/api/http.proto
curl -sSL $PROVENANCE_MAIN_URL/$GOOGLE_TYPES/api/httpbody.proto >$GOOGLE_TYPES/api/httpbody.proto

echo "Adding google/proto"
mkdir -p $GOOGLE_TYPES/protobuf
curl -sSL $PROVENANCE_MAIN_URL/$GOOGLE_TYPES/protobuf/any.proto >$GOOGLE_TYPES/protobuf/any.proto
curl -sSL $PROVENANCE_MAIN_URL/$GOOGLE_TYPES/protobuf/descriptor.proto >$GOOGLE_TYPES/protobuf/descriptor.proto

echo "Adding tm abci"
mkdir -p $TM_ABCI_TYPES
curl -sSL $PROVENANCE_MAIN_URL/$TM_ABCI_TYPES/types.proto >$TM_ABCI_TYPES/types.proto

echo "Adding tm crypto"
mkdir -p $TM_CRYPTO_TYPES
curl -sSL $PROVENANCE_MAIN_URL/$TM_CRYPTO_TYPES/proof.proto >$TM_CRYPTO_TYPES/proof.proto
curl -sSL $PROVENANCE_MAIN_URL/$TM_CRYPTO_TYPES/keys.proto >$TM_CRYPTO_TYPES/keys.proto

echo "Adding tm libs"
mkdir -p $TM_LIBS
curl -sSL $PROVENANCE_MAIN_URL/$TM_LIBS/types.proto >$TM_LIBS/types.proto

echo "Adding tm p2p"
mkdir -p $TM_P2P
curl -sSL $TM_URL/p2p/types.proto >$TM_P2P/types.proto

echo "Adding tm types"
mkdir -p $TM_TYPES
curl -sSL $PROVENANCE_MAIN_URL/$TM_TYPES/types.proto >$TM_TYPES/types.proto
curl -sSL $PROVENANCE_MAIN_URL/$TM_TYPES/evidence.proto >$TM_TYPES/evidence.proto
curl -sSL $PROVENANCE_MAIN_URL/$TM_TYPES/params.proto >$TM_TYPES/params.proto
curl -sSL $PROVENANCE_MAIN_URL/$TM_TYPES/validator.proto >$TM_TYPES/validator.proto
curl -sSL $TM_URL/types/block.proto >$TM_TYPES/block.proto

echo "Adding tm versions"
mkdir -p $TM_VERSION
curl -sSL $PROVENANCE_MAIN_URL/$TM_VERSION/types.proto >$TM_VERSION/types.proto

echo "Adding prov/attribute"
mkdir -p $PROVENANCE_TYPES/attribute/v1
curl -sSL $PROVENANCE_URL/attribute/v1/attribute.proto >$PROVENANCE_TYPES/attribute/v1/attribute.proto
curl -sSL $PROVENANCE_URL/attribute/v1/query.proto >$PROVENANCE_TYPES/attribute/v1/query.proto

echo "Adding prov/marker"
mkdir -p $PROVENANCE_TYPES/marker/v1
curl -sSL $PROVENANCE_URL/marker/v1/marker.proto >$PROVENANCE_TYPES/marker/v1/marker.proto
curl -sSL $PROVENANCE_URL/marker/v1/query.proto >$PROVENANCE_TYPES/marker/v1/query.proto
curl -sSL $PROVENANCE_URL/marker/v1/accessgrant.proto >$PROVENANCE_TYPES/marker/v1/accessgrant.proto

echo "Adding prov/metadata"
mkdir -p $PROVENANCE_TYPES/metadata/v1
curl -sSL $PROVENANCE_URL/metadata/v1/metadata.proto >$PROVENANCE_TYPES/metadata/v1/metadata.proto
curl -sSL $PROVENANCE_URL/metadata/v1/query.proto >$PROVENANCE_TYPES/metadata/v1/query.proto
curl -sSL $PROVENANCE_URL/metadata/v1/scope.proto >$PROVENANCE_TYPES/metadata/v1/scope.proto
curl -sSL $PROVENANCE_URL/metadata/v1/specification.proto >$PROVENANCE_TYPES/metadata/v1/specification.proto

echo "Adding prov/name"
mkdir -p $PROVENANCE_TYPES/name/v1
curl -sSL $PROVENANCE_URL/name/v1/name.proto >$PROVENANCE_TYPES/name/v1/name.proto
curl -sSL $PROVENANCE_URL/name/v1/query.proto >$PROVENANCE_TYPES/name/v1/query.proto

echo "Finished proto copy"
