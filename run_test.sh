#!/bin/bash
set -e
cd /work
CP=$(find /work/lib -iname "lucene-*.jar" | tr '\n' ':')
javac -cp "$CP" BuildTestIndex.java DeleteByIds.java
echo "COMPILE_OK"

rm -rf /tmp/test_index
mkdir -p /tmp/test_index

echo "--- building test index ---"
java -cp ".:$CP" BuildTestIndex /tmp/test_index

echo ""
echo "--- dry run: check doc2 and doc4 (should be found), plus doc99 (should be not found) ---"
printf "doc2\ndoc4\ndoc99\n" > /tmp/test_ids.txt
java -cp ".:$CP" DeleteByIds /tmp/test_index /tmp/test_ids.txt --dry-run

echo ""
echo "--- real delete: doc2 and doc4 (doc99 doesn't exist, should be harmless) ---"
java -cp ".:$CP" DeleteByIds /tmp/test_index /tmp/test_ids.txt

echo ""
echo "--- dry run again: doc2, doc4 should now be NOT FOUND; doc1,doc3,doc5 should remain ---"
printf "doc1\ndoc2\ndoc3\ndoc4\ndoc5\n" > /tmp/test_ids_all.txt
java -cp ".:$CP" DeleteByIds /tmp/test_index /tmp/test_ids_all.txt --dry-run
