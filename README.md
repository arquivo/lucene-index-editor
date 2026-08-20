# lucene-index-editor

A small standalone tool for directly editing a Solr/Lucene index on disk,
bypassing SolrCloud's distributed-update layer entirely.

## Why this exists

The `images` collection went through shard splits that left stale duplicate
documents behind on some `_1` sub-shards (the post-split cleanup step didn't
run correctly). Every attempt to delete these duplicates through Solr's
normal HTTP update API (`distrib=false`, `update.distrib=NONE`,
`update.distrib=FROMLEADER`) got silently rerouted by Solr's hash-based
document router to the *correct* shard instead of the intended (stale)
one - because Solr's distributed update layer always routes writes/deletes
to the document's true owning shard, regardless of which core's URL you
call. See session history for the full investigation.

This tool works around that by operating directly on the raw Lucene index
files with a standalone `IndexWriter`, matching the exact Lucene version
Solr 9.10.0 uses internally (Lucene 9.12.3), instead of going through
Solr's HTTP API at all.

## Approach

1. Run entirely inside a Docker image built from this repo's `Dockerfile`,
   which bundles the exact Lucene JARs that shipped with a given Solr
   release (see "Building the image" below) - so the Lucene JAR versions
   are guaranteed to match what actually wrote the index (avoids any
   index-format compatibility risk).
2. Never point the tool at an index directory that a running Solr JVM
   still has open - either operate on a **copy** of the core's index
   directory, or fully stop Solr on that node first if editing in place.
   Two writers (this tool + the running Solr JVM) touching the same
   Lucene index simultaneously will corrupt it.
3. Delete the known stale duplicate ids via `IndexWriter.deleteDocuments`,
   commit, and close.
4. Verify the result (open read-only with `CountDocs`/`--dry-run`,
   confirm exactly the intended ids are gone and nothing else changed).
5. Only after verification: make sure the corrected index is back in
   place for the real core (either swap the corrected copy back in, or
   restart Solr if edited in place), and reload/restart Solr.

   **Caveat when editing in place and restarting Solr**: this tool
   bypasses Solr's transaction log (tlog) entirely. If the core's tlog
   still holds an uncommitted `add` for one of the ids you just deleted
   (possible if the core hasn't hard-committed recently), Solr's
   `UpdateLog` replay on startup could silently re-insert it. Confirm a
   real hard commit has landed (check on-disk segment file timestamps,
   not just Solr's `STATUS` admin API) before stopping Solr for this kind
   of edit, and consider clearing/backing up the tlog directory if in
   doubt.

## Building the image

The `Dockerfile` is a multi-stage build: one stage pulls the official
`solr:<version>` image just to harvest its bundled Lucene JARs, a second
stage compiles this tool's `.java` files against those exact JARs with a
matching JDK, and a final stage packages just the compiled classes + JRE
+ JARs (no `javac`/build tooling) for a small, portable runtime image.

Build for whichever Solr/Java version pair matches the cluster you're
targeting, e.g.:

```sh
docker build \
  --build-arg SOLR_VERSION=9.10.0 \
  --build-arg JAVA_VERSION=17 \
  -t lucene-index-editor:9.10.0 .
```

(`SOLR_VERSION` defaults to `9.10.0` and `JAVA_VERSION` to `17` if
omitted - override both for a different cluster/version.)

## Usage

The image's entrypoint runs any of the tool's classes against a
`java -cp` classpath that already includes the bundled Lucene JARs -
just pass the class name and its arguments. Mount the index directory
(or its parent) into the container with `-v`.

### Counting documents

Read-only; opens the index with a plain `DirectoryReader`, never writes
anything. Useful to sanity-check a core's doc count before/after an edit.

```sh
docker run --rm \
  -v /path/to/some_core/data/index:/data/index \
  lucene-index-editor:9.10.0 \
  CountDocs /data/index
```

Reports live doc count, max doc id, and deleted-but-not-yet-purged doc
count.

### Deleting documents by id

Takes a text file with one document id per line (the unique key field is
assumed to be `id`, matching the `images` collection's schema).

Always dry-run first to confirm which ids are actually present, without
modifying anything:

```sh
docker run --rm \
  -v /path/to/some_core/data/index:/data/index \
  -v /path/to/ids_to_delete.txt:/data/ids.txt \
  lucene-index-editor:9.10.0 \
  DeleteByIds /data/index /data/ids.txt --dry-run
```

Then run the real delete (opens an `IndexWriter`, deletes the matching
ids via `deleteDocuments`, commits, and reports before/after doc counts):

```sh
docker run --rm \
  -v /path/to/some_core/data/index:/data/index \
  -v /path/to/ids_to_delete.txt:/data/ids.txt \
  lucene-index-editor:9.10.0 \
  DeleteByIds /data/index /data/ids.txt
```

Re-run the `--dry-run` form afterwards to confirm 0 of the target ids
remain, and `CountDocs` to confirm the doc count dropped by exactly the
expected amount.

**The index directory must not be open by a running Solr process** while
any of these commands run against it - either target a copy, or stop
Solr on that node first (see the tlog caveat above if editing in place).

## Status

Validated end-to-end against a real production-scale copy (56M+ docs,
`images_shard10_1_replica_n315` from p80): confirmed correct starting
doc count, confirmed all target duplicate ids present via `--dry-run`,
deleted them, and confirmed both the exact expected doc-count drop and
zero remaining matches on a follow-up dry run.

Not yet done: running this against a live core in place (stop Solr ->
edit -> restart), swapping a corrected copy back into a live core, and
publishing this image so it can be built on each affected node directly
(avoiding large scp transfers of whole core directories). Possibly also
publish the built image to Docker Hub later instead of building fresh on
each node.
