#!/bin/bash

shopt -s globstar
for i in ../src/**/*.java; do
  echo "process: $i"
  if ! grep -q '* Copyright the original author or authors.' $i
  then
    cat file-copyright.txt $i >$i.new && mv $i.new $i
  fi
done