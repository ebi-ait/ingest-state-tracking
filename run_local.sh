#!/bin/bash

if [ "$1" = "" ]
  then
    echo "Missing 1 param: start|stop|kill"
fi


if [ "$1" = "start" ]
  then
    docker-compose -p st up -d mongo rabbitmq redis core state-tracker broker validator ui
fi

if [ "$1" = "kill" ]
  then
    docker-compose -p st kill mongo rabbitmq redis core state-tracker broker validator ui
fi

if [ "$1" = "stop" ]
  then
    docker-compose -p st stop mongo rabbitmq redis core state-tracker broker validator ui
fi

