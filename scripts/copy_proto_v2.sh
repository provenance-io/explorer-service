#!/usr/bin/env bash

echo "version to download: prov: $1, cosmos: $2"
echo "Starting proto copy"

THIRD_PARTY="third_party"
PROV_ZIP_URL="https://github.com/provenance-io/provenance/releases/download/$1/protos-$1.zip"
COS_ZIP_URL="https://github.com/cosmos/cosmos-sdk/tarball/$2"

echo "Removing existing protos"
rm -r $THIRD_PARTY
mkdir -p $THIRD_PARTY

echo "Getting cos zip"
cd $THIRD_PARTY
curl -sSL $COS_ZIP_URL | tar --exclude='testutil/' --exclude='proto/ibc/' -xvf - --strip-components 1 --include '*.proto'
mv third_party/proto/* proto
rm -rf third_party

echo "Getting prov zip"
cd ../
curl -sSL $PROV_ZIP_URL >$THIRD_PARTY/protos.zip
unzip -o $THIRD_PARTY/protos.zip -d $THIRD_PARTY
rm $THIRD_PARTY/protos.zip

echo "Finished proto copy"
