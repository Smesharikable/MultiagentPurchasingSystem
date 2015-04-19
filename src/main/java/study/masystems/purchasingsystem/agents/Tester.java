package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import jade.core.Agent;
import jade.util.Logger;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.json.JSONObject;
import study.masystems.purchasingsystem.GoodInformation;
import study.masystems.purchasingsystem.GoodNeed;

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

            Scanner fileScanner = new Scanner(new File(testDataFilename)).useDelimiter("\\Z");
            String testData = fileScanner.next();
            fileScanner.close();

            JSONObject agents = new JSONObject(testData);

            String JSONAgentString;
            AgentController newAgent;

            for (String agentName : agents.keySet())
            {
                JSONObject JSONAgent = agents.getJSONObject(agentName);
                String className = JSONAgent.getString("class");

                switch (className){
                    case "study.masystems.purchasingsystem.agents.Customer":
                    case "study.masystems.purchasingsystem.agents.Buyer":
                        JSONAgentString = JSONAgent.getJSONObject("goodNeeds").toString();
                        Object goodNeeds = new JSONDeserializer<Map<String, GoodNeed>>()
                                .use("values", GoodNeed.class)
                                .deserialize(JSONAgentString);
                        Object money = JSONAgent.getInt("money");

                        newAgent = container.createNewAgent(agentName, className, new Object[]{goodNeeds, money});
                        newAgent.start();
                        break;

                    case "study.masystems.purchasingsystem.agents.Supplier":
                        JSONAgentString = JSONAgent.getJSONObject("goods").toString();
                        Object goods = new JSONDeserializer<HashMap<String, GoodInformation>>()
                                .use("values", GoodInformation.class)
                                .deserialize(JSONAgentString);

                        newAgent = container.createNewAgent(agentName, className, new Object[]{goods});
                        newAgent.start();
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
