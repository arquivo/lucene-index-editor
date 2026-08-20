import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;

/**
 * Builds a tiny throwaway Lucene index with a handful of docs, for testing
 * DeleteByIds without touching any real data.
 */
public class BuildTestIndex {
    public static void main(String[] args) throws Exception {
        String indexDir = args[0];
        try (FSDirectory dir = FSDirectory.open(Paths.get(indexDir))) {
            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            try (IndexWriter writer = new IndexWriter(dir, config)) {
                for (String id : new String[]{"doc1", "doc2", "doc3", "doc4", "doc5"}) {
                    Document doc = new Document();
                    doc.add(new StringField("id", id, Field.Store.YES));
                    writer.addDocument(doc);
                }
                writer.commit();
            }
        }
        System.out.println("Test index built at " + indexDir + " with 5 docs.");
    }
}
