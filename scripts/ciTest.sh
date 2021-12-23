#!/bin/bash

set -o xtrace

echo "************************************** Launch tests ******************************************"

file='./ci/version'
VERSION_NUMBER=$(<"$file")

echo "Launch tests for $VERSION_NUMBER"

docker build --rm -f scripts/docker/Dockerfile-test.build --build-arg VERSION_NUMBER=$VERSION_NUMBER -t  cytomine/cytomine-core-spring-test .
mkdir -p ./ci/reports/test

containerId=$(docker create --network scripts_default --link nginxTest:localhost-core --link postgresqltest:postgresqltest --link mongodbtest:mongodbtest --link rabbitmqtest:rabbitmqtest  cytomine/cytomine-core-spring-test )
#docker network connect scripts_default $containerId
docker start -ai  $containerId
docker cp /app/build/test-results "$PWD"/ci/reports
docker rm $containerId