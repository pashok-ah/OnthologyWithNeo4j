import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by P. Akhmedzianov on 23.04.2016.
 */

public class Ontology {
    public enum RelTypes implements RelationshipType {
        SUPERCLASS_OF, SUBCLASS_OF, INSTANCE_OF
    }

    public enum NodeTypes implements Label {
        CLASS, INSTANCE
    }

    private GraphDatabaseService db;

    private ToNeo4jImporter importer;

    private HashMap<String, Node> nodesMap = new HashMap<>();

    public Ontology(final GraphDatabaseService db, final String inputFilePath) {
        this.db = db;
        this.importer = new ToNeo4jImporter(inputFilePath, db);

        this.nodesMap = importer.tryInitializeDatabase();

        printAllPropertiesByName("pablus");
    }

    private void printAllPropertiesByName(String name) {
        ArrayList<Node> superclassNodesList = findSuperclassNodesByName(name, true);
        HashMap<String, String> propertiesMap = new HashMap<>();

        try (Transaction tx = db.beginTx()) {
            for (Node superclassNode : superclassNodesList) {
                for (Map.Entry<String, Object> entry : superclassNode.getAllProperties().entrySet()) {
                    if (!propertiesMap.containsKey(entry.getKey())) {
                        propertiesMap.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            tx.success();
        }
    }

    


    private ArrayList<Node> findSuperclassNodesByName(String name, boolean isIncludeStart) {
        ArrayList<Node> superclassNodes = new ArrayList<>();
        Node startNode = nodesMap.get(name);
        if(isIncludeStart) superclassNodes.add(startNode);
        try (Transaction tx = db.beginTx()) {
            org.neo4j.graphdb.traversal.Traverser superclassTraverser =
                    findUpwardNodesWithoutStart(startNode);
            for (Path superclassPath : superclassTraverser) {
                superclassNodes.add(superclassPath.endNode());
            }
            tx.success();
        }
        return superclassNodes;
    }

    private org.neo4j.graphdb.traversal.Traverser findUpwardNodesWithoutStart(final Node startNode) {
        TraversalDescription td = db.traversalDescription()
                .breadthFirst()
                .relationships(RelTypes.SUBCLASS_OF, Direction.OUTGOING)
                .relationships(RelTypes.INSTANCE_OF, Direction.OUTGOING)
                .evaluator(Evaluators.excludeStartPosition());
        return td.traverse(startNode);
    }
}
