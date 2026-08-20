#!/bin/sh
# Usage: docker run ... <image> <ClassName> [args...]
# e.g.:  docker run --rm -v $(pwd)/indexes:/indexes <image> \
#            CountDocs /indexes/some_core/data/index
set -e

if [ "$#" -lt 1 ]; then
    echo "Usage: <ClassName> [args...]" >&2
    echo "Available classes: CountDocs, DeleteByIds, BuildTestIndex" >&2
    exit 1
fi

CLASS="$1"
shift

CP="/app:$(find /opt/lucene-libs -name '*.jar' | tr '\n' ':')"
exec java -cp "$CP" "$CLASS" "$@"
