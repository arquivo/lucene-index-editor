#!/bin/bash
set -e
cd /work
CP=$(find /work/lib -iname "lucene-*.jar" | tr '\n' ':')
javac -cp "$CP" DeleteByIds.java CountDocs.java BuildTestIndex.java && echo COMPILE_OK
