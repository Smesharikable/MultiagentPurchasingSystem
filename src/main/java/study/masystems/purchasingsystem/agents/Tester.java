package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import jade.core.Agent;
import jade.util.Logger;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.json.JSONObject;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseProposal;

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

            Scanner fileScanner = new Scanner(new File("." + File.separator + "test" + File.separator + testDataFilename)).useDelimiter("\\Z");
            String testData = fileScanner.next();
            fileScanner.close();

            JSONObject agents = new JSONObject(testData);
            //System.out.println(agents.toString(1));

            String JSONAgentString;
            AgentController newAgent;

            /*StringBuilder sniffList = new StringBuilder("df;");
            for (String agentName : agents.keySet()) {
                sniffList.append(agentName).append(";");
            }
            sniffList.deleteCharAt(sniffList.length()-1);
            System.out.println(sniffList);
            AgentController sniffer = container.createNewAgent("sniffer11", jade.tools.sniffer.Sniffer.class.getName(), new Object[]{sniffList});
            sniffer.start();
            System.out.println(sniffer.getState());

            while (!(sniffer.getState().getName().equalsIgnoreCase("active")));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(sniffer.getState());*/

            for (String agentName : agents.keySet()) {
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
                        logger.log(Logger.INFO, "Agent " + className.substring(40) + " : " + agentName + " created");
                        break;

                    case "study.masystems.purchasingsystem.agents.Supplier":
                        JSONAgentString = JSONAgent.getJSONObject("goods").toString();
                        Object goods = new JSONDeserializer<HashMap<String, PurchaseProposal>>()
                                            .use("values", PurchaseProposal.class)
                                            .deserialize(JSONAgentString);

                        newAgent = container.createNewAgent(agentName, className, new Object[]{goods});
                        newAgent.start();
                        logger.log(Logger.INFO, "Agent " + className.substring(40) + " : " + agentName + " created");
                        break;

                    default:
                        logger.log(Logger.WARNING, "Unrecognized agent class " + className);
                }
            }

        } catch (FileNotFoundException e) {
            logger.log(Logger.WARNING, "testData.json not found");
        } catch (StaleProxyException e) {
            logger.log(Logger.WARNING, e.toString());
        }
    }
}
