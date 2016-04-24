import org.neo4j.graphdb.GraphDatabaseService;

import com.google.common.io.Files;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by P. Akhmedzianov on 23.04.2016.
 */
public class ToNeo4jImporter {
    public static final String CLASSES_TAG_NAME = "classes";
    public static final String RELATIONSHIPS_TAG_NAME = "relationships";
    public static final String INSTANCES_TAG_NAME = "instances";

    public static final String ELEMENTS_DELIMITER = ";";
    public static final String PROPERTIES_DELIMITER = ",";

    private GraphDatabaseService db;
    private String inputLine;

    private HashMap<String, Node> nodesMap = new HashMap<>();

    public ToNeo4jImporter(final String path, final GraphDatabaseService db) {
        try {
            this.inputLine = Files.toString(new File(path), Charset.defaultCharset())
                    .replace("\n", "").replace("\r", "").replace(" ", "");
            this.db = db;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryInitializeDatabase() {
        try (Transaction tx = db.beginTx()) {
            createClasses(getStringByTagName(CLASSES_TAG_NAME));
            createRelationships(getStringByTagName(RELATIONSHIPS_TAG_NAME));
            createInstances(getStringByTagName(INSTANCES_TAG_NAME));
            tx.success();
        }
    }

    private void createClasses(final String input) {
        String[] classesStrings = input.split(ELEMENTS_DELIMITER);
        for (String classString : classesStrings) {
            try {
                String className = classString.split("\\{")[0];
                Node newNode = db.createNode();
                newNode.addLabel(Ontology.NodeTypes.CLASS);
                newNode.setProperty("name", className);
                if(classString.length()-className.length() > 2) {
                    HashMap<String, String> properties = getProperties(classString);
                    for (Map.Entry<String, String> entry : properties.entrySet()) {
                        newNode.setProperty(entry.getKey(), entry.getValue());
                    }
                }
                nodesMap.put(className, newNode);
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("createClasses " + input);
                e.printStackTrace();
            }
        }
    }

    private void createRelationships(final String input) {
        String[] relationshipStrings = input.split(ELEMENTS_DELIMITER);
        for (String relationshipString : relationshipStrings) {
            try {
                String[] twoClassNames = relationshipString.split("->");
                Node firstNode = nodesMap.get(twoClassNames[0]);
                Node secondNode = nodesMap.get(twoClassNames[1]);
                firstNode.createRelationshipTo(secondNode,
                        Ontology.RelTypes.SUBCLASS_OF);

            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("createRelationships " + relationshipString);
                e.printStackTrace();
            } catch (NullPointerException e) {
                System.out.println("createRelationships " + relationshipString + " maybe class Name not found");
                e.printStackTrace();
            }
        }

    }

    private void createInstances(final String input) {
        String[] instanceStrings = input.split(ELEMENTS_DELIMITER);
        for (String instanceString : instanceStrings) {
            try {
                String[] instanceArray = instanceString.split("\\{")[0].split(":");
                String instanceName = instanceArray[0];
                String instanceClassName = instanceArray[1];
                HashMap<String, String> properties = getProperties(instanceString);

                Node newNode = db.createNode();
                newNode.addLabel(Ontology.NodeTypes.INSTANCE);
                newNode.setProperty("name", instanceName);
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    newNode.setProperty(entry.getKey(), entry.getValue());
                }
                newNode.createRelationshipTo(nodesMap.get(instanceClassName), Ontology.RelTypes.INSTANCE_OF);
                nodesMap.put(instanceName, newNode);
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("createInstances " + input);
                e.printStackTrace();
            } catch (NullPointerException e) {
                System.out.println("createInstances " + input + " maybe class Name not found");
                e.printStackTrace();
            }
        }

    }

    private String getStringByTagName(final String tagName) {
        Pattern p = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">");
        return findString(p, inputLine);
    }

    private HashMap<String, String> getProperties(final String classString) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");

        String propertiesString = findString(p, classString);
        HashMap<String, String> resultMap = new HashMap<>();
        for (String property : propertiesString.split(PROPERTIES_DELIMITER)) {
            String[] properyAndValue = property.split(":");
            if (properyAndValue.length == 2) {
                resultMap.put(properyAndValue[0], properyAndValue[1]);
            } else {
                throw new IllegalArgumentException("getProperties wrong property:" + property);
            }

        }
        return resultMap;
    }

    private String findString(final Pattern pattern, final String inputString) {
        Matcher matcher = pattern.matcher(inputString);
        while (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("findString " + inputString);
    }

}
