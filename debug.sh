#!/usr/bin/env bash
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -DdevMode=true -jar target/generator-swarm.jar
