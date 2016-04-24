import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by P. Akhmedzianov on 23.04.2016.
 */
public class Main {
    static final File STORE_DIRECTORY_FILE = new File("G:\\Neo4j\\ontology");
    static final String INPUT_FILE_PATH = "input/ontology.data";

    public static void main(String[] args){
        try {
            FileUtils.deleteRecursively( STORE_DIRECTORY_FILE );
            GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
            GraphDatabaseService db = dbFactory.newEmbeddedDatabase(STORE_DIRECTORY_FILE);
            registerShutdownHook(db);

            Ontology ontology = new Ontology(db, INPUT_FILE_PATH);
            ontology.test();
            db.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }



}
