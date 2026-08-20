# Builds a small, portable image containing this tool's compiled classes
# plus the exact Lucene JARs bundled with a given Solr release - so the
# tool always matches the on-disk index format it will operate on.
#
# Build for a specific Solr / Java version pair, e.g.:
#   docker build --build-arg SOLR_VERSION=9.10.0 --build-arg JAVA_VERSION=17 \
#       -t lucene-index-editor:9.10.0 .
#
# Then run any of the tool's classes against a mounted index directory:
#   docker run --rm -v /path/to/indexes:/indexes lucene-index-editor:9.10.0 \
#       CountDocs /indexes/images_shard10_1_replica_n315/data/index

ARG SOLR_VERSION=9.10.0
ARG JAVA_VERSION=17

# --- Stage 1: source of truth for this Solr version's Lucene JARs ---
FROM solr:${SOLR_VERSION} AS solr-libs

# --- Stage 2: compile against those exact JARs, using a matching JDK ---
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build

# Lucene JARs live under server/solr-webapp/.../WEB-INF/lib in every
# recent Solr release, with a few extras (e.g. analysis-extras) under
# modules/. Grab all of them so future tools in this repo can use
# analyzers etc. if needed; unused JARs cost nothing at compile time.
RUN mkdir -p /opt/lucene-libs
COPY --from=solr-libs /opt/solr /opt/solr-src
RUN find /opt/solr-src -iname 'lucene-*.jar' -exec cp {} /opt/lucene-libs/ \;

WORKDIR /work
COPY *.java ./
RUN CP=$(find /opt/lucene-libs -name '*.jar' | tr '\n' ':') && \
    javac -cp "$CP" *.java

# --- Stage 3: lightweight runtime - JRE only, no javac/build tooling ---
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

COPY --from=build /opt/lucene-libs /opt/lucene-libs
COPY --from=build /work/*.class /app/

WORKDIR /app
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
