#!/bin/sh

# This script requires Maven to be installed.

echo 'Building Camel Form Emailer....'

./mvnw package -Pnative -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman

echo 'Produced native executable!'
