#!/bin/bash

echo "Checking out master"
git checkout master

echo "Pulling changes from github"
git pull

echo "Running Integration Tests Suite"
./gradlew integrationTest
