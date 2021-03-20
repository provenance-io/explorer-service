#!/bin/bash


function up {
  docker volume prune -f
  docker-compose -f service/docker/docker-compose-db.yml up --build -d
  docker ps -a
}

function down {
  docker-compose -f service/docker/docker-compose-db.yml down
}

function bounce {
   down
   up
}

# Leaves the volume in place
function up-cached {
  docker-compose -f service/docker/docker-compose-db.yml up
  docker ps -a
}

function stop {
  docker-compose -f docker/docker-compose-db.yml stop
}

${1}
