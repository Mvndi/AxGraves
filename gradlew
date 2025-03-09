#!/bin/bash
mvn package
mkdir -p build/libs
cp target/AxGraves-1.18.0.jar build/libs/AxGraves-1.18.0.jar
