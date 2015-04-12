package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import jade.core.Agent;
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

/**
 * Created by Kimbaet on 11.04.2015.
 */
public class Tester extends Agent{
    private AgentContainer container;

    @Override
    protected void setup() {
        container = getContainerController();

        try {
            Scanner fileScanner = new Scanner(new File("test.json")).useDelimiter("\\Z");
            String test = fileScanner.next();
            fileScanner.close();

            JSONObject agents = new JSONObject(test);
            System.out.println(agents.toString(1));

            for (String a : agents.keySet())
            {
                JSONObject JSONagent = agents.getJSONObject(a);
                String className = JSONagent.getString("class");

                switch (className){
                    case "study.masystems.purchasingsystem.agents.Customer":
                    case "study.masystems.purchasingsystem.agents.Buyer":
                        Object needs = new JSONDeserializer<Map<String, GoodNeed>>().deserialize(JSONagent.getJSONObject("goodNeeds").toString());
                        System.out.println(needs);
                        Object money = JSONagent.getInt("money");
                        System.out.println(money);
                        AgentController newAgent = container.createNewAgent(a, className, new Object[]{needs, money});
                        newAgent.start();
                        break;
                    case "study.masystems.purchasingsystem.agents.Supplier":
                        Object goods = new JSONDeserializer<HashMap<String, PurchaseProposal>>().deserialize(JSONagent.getJSONObject("goodNeeds").toString());
                        AgentController newAgent1 = container.createNewAgent(a, className, new Object[]{goods});
                        newAgent1.start();
                        break;
                }
            }

        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (StaleProxyException e) {
            System.out.println(e.toString());
        }
    }
}
