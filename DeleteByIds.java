import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * DeleteByIds - deletes a list of document ids directly from a Lucene index
 * directory on disk, bypassing SolrCloud entirely.
 *
 * Usage:
 *   java DeleteByIds <index_dir> <ids_file> [--dry-run]
 *
 *   <index_dir>  Path to the Lucene index directory (e.g. .../data/index)
 *                This MUST be a directory that no running Solr core has
 *                open (unload the core first, or operate on a copy).
 *   <ids_file>   Text file with one document id per line to delete.
 *   --dry-run    If present, only reports how many of the given ids are
 *                currently found in the index (via a TermQuery search),
 *                without deleting or writing anything.
 *
 * The unique key field is assumed to be "id" (a string field), matching
 * the images collection's schema.
 */
public class DeleteByIds {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java DeleteByIds <index_dir> <ids_file> [--dry-run]");
            System.exit(1);
        }

        String indexDir = args[0];
        String idsFile = args[1];
        boolean dryRun = args.length > 2 && args[2].equals("--dry-run");

        List<String> ids = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(idsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    ids.add(line);
                }
            }
        }

        System.out.println("Loaded " + ids.size() + " id(s) from " + idsFile);

        try (FSDirectory dir = FSDirectory.open(Paths.get(indexDir))) {

            if (dryRun) {
                runDryRun(dir, ids);
                return;
            }

            IndexWriterConfig config = new IndexWriterConfig();
            // OpenMode.APPEND (the default) - we are editing an existing
            // index, never creating a new one.
            try (IndexWriter writer = new IndexWriter(dir, config)) {
                int before = writer.getDocStats().numDocs;
                System.out.println("Index has " + before + " docs before delete.");

                Term[] terms = new Term[ids.size()];
                for (int i = 0; i < ids.size(); i++) {
                    terms[i] = new Term("id", ids.get(i));
                }

                long deleted = writer.deleteDocuments(terms);
                System.out.println("deleteDocuments reported " + deleted + " (internal sequence number, not doc count).");

                writer.commit();

                int after = writer.getDocStats().numDocs;
                System.out.println("Index has " + after + " docs after delete and commit.");
                System.out.println("Net change: " + (before - after));
            }
        }
    }

    private static void runDryRun(FSDirectory dir, List<String> ids) throws Exception {
        try (IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            int found = 0;
            for (String id : ids) {
                TopDocs docs = searcher.search(new TermQuery(new Term("id", id)), 1);
                if (docs.totalHits.value > 0) {
                    found++;
                } else {
                    System.out.println("NOT FOUND: " + id);
                }
            }
            System.out.println("Dry run: " + found + " / " + ids.size() + " id(s) found in index.");
        }
    }
}
