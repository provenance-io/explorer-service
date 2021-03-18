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

function bounce-cached {
  down
  up-cached
}


${1}
