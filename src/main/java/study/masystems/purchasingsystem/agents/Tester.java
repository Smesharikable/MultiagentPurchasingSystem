package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import jade.core.Agent;
import jade.util.Logger;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.json.JSONObject;
import study.masystems.purchasingsystem.*;
import study.masystems.purchasingsystem.jgrapht.WeightedEdge;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Tester extends Agent{
    private static Logger logger = Logger.getMyLogger("Buyer");

    @Override
    protected void setup() {
        AgentContainer container = getContainerController();
        String testDataFilename;

        try {
            Object[] args = getArguments();
            if (args == null || args.length == 0) {
                testDataFilename = "testData.json";
            } else {
                testDataFilename = (String) args[0];
            }

            String testFilename = String.join(File.separator, ".", "src", "test", "configuration", testDataFilename);

            Scanner testFileScanner = new Scanner(new File(testFilename)).useDelimiter("\\Z");
            String testData = testFileScanner.next();
            testFileScanner.close();
            
            JSONObject agents = new JSONObject(testData);

            String JSONAgentString;
            AgentController newAgent;

            SimpleWeightedGraph<Integer, WeightedEdge> graph = new CityGraphBuilder().read();
            FloydWarshallShortestPaths<Integer, WeightedEdge> shortestPaths
                    = new FloydWarshallShortestPaths<>(graph);
            final GraphPath<Integer, WeightedEdge> shortestPath = shortestPaths.getShortestPath(7, 19);


            for (String agentName : agents.keySet())
            {
                JSONObject JSONAgent = agents.getJSONObject(agentName);
                String className = JSONAgent.getString("class");

                JSONObject JSONConstants = null;
                if (JSONAgent.has("constants")) {
                    JSONConstants = JSONAgent.getJSONObject("constants");
                }

                Object constants = null;
                switch (className){
                    case "study.masystems.purchasingsystem.agents.Customer":
                        if (JSONConstants != null) {
                            constants = new JSONDeserializer<CustomerConstants>().deserialize(JSONConstants.toString());
                        }
                    case "study.masystems.purchasingsystem.agents.Buyer":
                        JSONAgentString = JSONAgent.getJSONObject("goodNeeds").toString();
                        Object goodNeeds = new JSONDeserializer<Map<String, GoodNeed>>()
                                .use("values", GoodNeed.class)
                                .deserialize(JSONAgentString);
                        Object money = JSONAgent.getInt("money");
                        if (JSONConstants != null && constants == null) {
                            constants = new JSONDeserializer<BuyerConstants>().deserialize(JSONConstants.toString());
                        }
                        //JSONObject JSONPath = JSONAgent.getJSONObject("path");
                        //Object cityPath = new JSONDeserializer<CityPath>().deserialize(JSONPath.toString());

                        newAgent = container.createNewAgent(agentName, className, new Object[]{shortestPaths, goodNeeds, money, null, constants});
                        newAgent.start();

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;

                    case "study.masystems.purchasingsystem.agents.Supplier":
                        JSONAgentString = JSONAgent.getJSONObject("goods").toString();
                        Object goods = new JSONDeserializer<HashMap<String, GoodInformation>>()
                                .use("values", GoodInformation.class)
                                .deserialize(JSONAgentString);

                        newAgent = container.createNewAgent(agentName, className, new Object[]{goods});
                        newAgent.start();
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;

                    default:
                        logger.log(Logger.WARNING, "Unrecognized agent class " + className);
                }
            }

        } catch (FileNotFoundException e) {
            logger.log(Logger.WARNING, "*.json not found");
        } catch (StaleProxyException e) {
            logger.log(Logger.WARNING, e.toString());
        }
    }
}
