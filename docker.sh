#!/bin/bash
VERSION=$(gradle -q pV | head -n 1)
BUILDINDEX=$1
#git tag $TAG_FLAGS $VERSION &&
#./build.sh &&
echo "VERSION=${VERSION}"
docker build --build-arg VERSION=$VERSION -t perspicaio/opentsdb:$VERSION-$BUILDINDEX .
#&& docker push perspicaio/opentsdb:$VERSION-$BUILDINDEX && git push $TAG_FLAGS origin master --tags
