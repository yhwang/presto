#!/bin/bash

set -e

if [ "$#" != "1" ]; then
    echo "usage: build.sh <version>"
    exit 1
fi

VERSION=$1
TAG="${TAG:-latest}"
ARCHS=${ARCHS:-linux/amd64,linux/arm64,linux/ppc64le}
REGISTRY=${REGISTRY:-docker.io}

wget --quiet https://repo1.maven.org/maven2/com/facebook/presto/presto-server/${VERSION}/presto-server-${VERSION}.tar.gz
wget --quiet https://repo1.maven.org/maven2/com/facebook/presto/presto-cli/${VERSION}/presto-cli-${VERSION}-executable.jar

docker buildx create --name mycontainer --bootstrap --use
docker buildx build --builder=mycontainer --platform="${ARCHS}" --force-rm --quiet \
    -t "${REGISTRY}/yihongwang/presto:${TAG}" --build-arg="PRESTO_VERSION=${VERSION}" --push -f Dockerfile .

docker buildx rm mycontainer
