import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;

import java.util.*;


/**
 * Created by P. Akhmedzianov on 23.04.2016.
 */

public class Ontology {
    public enum RelTypes implements RelationshipType {
        SUBCLASS_OF, INSTANCE_OF
    }

    public enum NodeTypes implements Label {
        CLASS, INSTANCE
    }

    private GraphDatabaseService db;

    private ToNeo4jImporter importer;

    private void initializeDatabase() {
        importer.tryInitializeDatabase();
    }

    public Ontology(final GraphDatabaseService db, final String inputFilePath) {
        this.db = db;
        this.importer = new ToNeo4jImporter(inputFilePath, db);
        this.initializeDatabase();
    }

    private void printProperties(HashMap<String, String> propertiesMap) {
        for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    private void printNodes(Collection<Node> nodesCollection) {
        try (Transaction tx = db.beginTx()) {
            String resultString = "";
            for (Node node:nodesCollection){
                resultString+=node.getLabels().iterator().next().name()+" : "+
                        node.getProperty("name") +", ";
            }
            System.out.println(resultString);
                tx.success();
        }
    }

    public void test() {
        System.out.println("Get properties test...");
        String classNameToFindproperties = "Bireme";
        System.out.println("Bireme{");
        printProperties(getAllPropertiesByNode(findNodeByNameAndType(classNameToFindproperties, NodeTypes.CLASS)));
        System.out.println("}");
        System.out.println("Get all instances test...");
        String classNameToFindInstances = "SailShip";
        System.out.println("Searching instances of class "+classNameToFindInstances);
        printNodes(findAllSubnodes(classNameToFindInstances, RelTypes.INSTANCE_OF));
        System.out.println("Get all subclasses test...");
        String classNameToFindSubclasses = "Longship";
        System.out.println("Searching subclasses of class "+classNameToFindSubclasses);
        printNodes(findAllSubnodes(classNameToFindSubclasses, RelTypes.SUBCLASS_OF));
        System.out.println("Is instance test...");
        System.out.println("Yamato is instance of Drekkar = " + isInstaceOf("Yamato", "Drekkar"));
        System.out.println("Yamato is instance of TurbineShip = " + isInstaceOf("Yamato", "TurbineShip"));
        System.out.println("Has property test...");
        System.out.println("HMS_Victory hasProperty country with val Empire_of_Japan = " +
                hasProperty("HMS_Victory", NodeTypes.INSTANCE, "country", "Empire_of_Japan"));
        System.out.println("HMS_Victory hasProperty country with val British_Empire = " +
                hasProperty("HMS_Victory", NodeTypes.INSTANCE, "country", "British_Empire"));
        System.out.println("HMS_Victory hasProperty mission with val sea_battles = " +
                hasProperty("HMS_Victory", NodeTypes.INSTANCE, "mission", "sea_battles"));
        System.out.println("Find all nodes with property test...");
        System.out.println("Finding all nodes with propery material value metal");
        printNodes(findAllNodesWithPropertyValue("material", "metal", true));
        System.out.println("Get value of property test...");
        System.out.println("USS_Constitution getProperty armament  = " +
                getValueOfProperty("USS_Constitution", NodeTypes.INSTANCE, "armament"));
        System.out.println("USS_Constitution getProperty propulsion  = " +
                getValueOfProperty("USS_Constitution", NodeTypes.INSTANCE, "propulsion"));
    }

    private HashMap<String, String> getAllPropertiesByNode(final Node startNode) {
        org.neo4j.graphdb.traversal.Traverser traverser = findUpwardNodesWithoutStart(startNode);
        ArrayList<Node> superclassNodesList = getNodesFromTraverser(traverser);
        HashMap<String, String> propertiesMap = new HashMap<>();

        try (Transaction tx = db.beginTx()) {
            for (Node superclassNode : superclassNodesList) {
                for (Map.Entry<String, Object> entry : superclassNode.getAllProperties().entrySet()) {
                    if (!propertiesMap.containsKey(entry.getKey())) {
                        propertiesMap.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            // finally adding properties of start node
            for (Map.Entry<String, Object> entry : startNode.getAllProperties().entrySet()) {
                propertiesMap.put(entry.getKey(), entry.getValue().toString());
            }
            tx.success();
        }
        return propertiesMap;
    }

    private ArrayList<Node> findAllSubnodes(final String name, final RelTypes relationshipTypeToInclude) {
        Node startNode = findNodeByNameAndType(name, NodeTypes.CLASS);
        org.neo4j.graphdb.traversal.Traverser traverser =
                findDownwardNodesWithoutStart(startNode, relationshipTypeToInclude);
        return getNodesFromTraverser(traverser);
    }

    private boolean isInstaceOf(String instanceName, String className) {
        Node startNode = findNodeByNameAndType(instanceName, NodeTypes.INSTANCE);
        org.neo4j.graphdb.traversal.Traverser traverser = findUpwardNodesWithoutStart(startNode);
        ArrayList<Node> classNodesOfStartNode = getNodesFromTraverser(traverser);
        try (Transaction tx = db.beginTx()) {
            for (Node node : classNodesOfStartNode) {
                if (node.hasProperty("name") && node.getProperty("name").equals(className))
                    return true;
            }
            tx.success();
        }
        return false;
    }

    private boolean hasProperty(final String name, final NodeTypes nodeType,
                                final String propertyName,
                                final String propertyValue) {
        Node startNode = findNodeByNameAndType(name, nodeType);
        HashMap<String, String> allNodeProperties = getAllPropertiesByNode(startNode);
        return allNodeProperties.containsKey(propertyName) &&
                allNodeProperties.get(propertyName).equals(propertyValue);
    }

    private String getValueOfProperty(final String name, final NodeTypes nodeType,
                                      final String propertyName) {
        Node startNode = findNodeByNameAndType(name, nodeType);
        HashMap<String, String> allNodeProperties = getAllPropertiesByNode(startNode);
        if (allNodeProperties.containsKey(propertyName)) {
            return allNodeProperties.get(propertyName);
        } else {
            return "";
        }
    }

    private HashSet<Node> findAllNodesWithPropertyValue(final String propertyName,
                                                        final String propertyValue,
                                                        final boolean isIncludeInherited) {
        HashSet<Node> nodesWithPropertySet = new HashSet<>();
        String query = "match (n {" + propertyName + ": '" + propertyValue + "'}) " +
                "return n";
        try (Transaction ignored = db.beginTx();
             Result result = db.execute(query)) {
            while (result.hasNext()) {
                Map<String, Object> row = result.next();
                for (Map.Entry<String, Object> column : row.entrySet()) {
                    nodesWithPropertySet.add((Node) column.getValue());
                }
            }

            if (isIncludeInherited && nodesWithPropertySet.size() > 0) {
                for (Node node : nodesWithPropertySet) {
                    org.neo4j.graphdb.traversal.Traverser traverser =
                            findDownwardNodesWithoutStart(node);
                    ArrayList<Node> downwardNodes = getNodesFromTraverser(traverser);
                    for (Node downwardNode : downwardNodes) {
                        if (!downwardNode.hasProperty(propertyName) ||
                                downwardNode.getProperty(propertyName).equals(propertyValue)) {
                            nodesWithPropertySet.add(downwardNode);
                        }
                    }
                }
            }
        }
        return nodesWithPropertySet;
    }


    private ArrayList<Node> getNodesFromTraverser(final org.neo4j.graphdb.traversal.Traverser traverser) {
        ArrayList<Node> superclassNodes = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            if (traverser != null) {
                for (Path superclassPath : traverser) {
                    superclassNodes.add(superclassPath.endNode());
                }
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

    private org.neo4j.graphdb.traversal.Traverser findDownwardNodesWithoutStart(
            final Node startNode, final RelTypes relationshipTypeToInclude) {
        TraversalDescription td = db.traversalDescription()
                .breadthFirst()
                .relationships(RelTypes.SUBCLASS_OF, Direction.INCOMING)
                .relationships(RelTypes.INSTANCE_OF, Direction.INCOMING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator(
                        Evaluators.includeWhereLastRelationshipTypeIs(relationshipTypeToInclude));
        return td.traverse(startNode);
    }

    private org.neo4j.graphdb.traversal.Traverser findDownwardNodesWithoutStart(
            final Node startNode) {
        TraversalDescription td = db.traversalDescription()
                .breadthFirst()
                .relationships(RelTypes.SUBCLASS_OF, Direction.INCOMING)
                .relationships(RelTypes.INSTANCE_OF, Direction.INCOMING)
                .evaluator(Evaluators.excludeStartPosition());
        return td.traverse(startNode);
    }

    private Node findNodeByNameAndType(final String name, final NodeTypes type) {
        Node resultNode;
        try (Transaction tx = db.beginTx()) {
            resultNode = db.findNode(type, "name", name);
            tx.success();
        }
        return resultNode;
    }
}
