import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;

/**
 * CountDocs - opens a Lucene index directory read-only and reports the
 * number of live (non-deleted) documents, plus max doc and deleted doc
 * counts. Does not modify anything on disk.
 *
 * Usage:
 *   java CountDocs <index_dir>
 */
public class CountDocs {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java CountDocs <index_dir>");
            System.exit(1);
        }

        String indexDir = args[0];

        try (FSDirectory dir = FSDirectory.open(Paths.get(indexDir));
             IndexReader reader = DirectoryReader.open(dir)) {
            int numDocs = reader.numDocs();
            int maxDoc = reader.maxDoc();
            int deletedDocs = maxDoc - numDocs;

            System.out.println("Index dir:    " + indexDir);
            System.out.println("Live docs:    " + numDocs);
            System.out.println("Max doc id:   " + maxDoc);
            System.out.println("Deleted docs: " + deletedDocs);
        }
    }
}
