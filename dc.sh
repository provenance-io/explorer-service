#!/bin/bash


function up {
  docker volume prune -f
  docker-compose -f service/docker/dependencies.yml up --build -d
  docker ps -a
}

function down {
  docker-compose -f service/docker/dependencies.yml down
}

function bounce {
   down
   up
}

${1}
