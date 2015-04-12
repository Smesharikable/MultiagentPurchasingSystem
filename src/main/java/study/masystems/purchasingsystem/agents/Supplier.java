package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseProposal;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Product supplier.
 */
public class Supplier extends Agent {

    private HashMap<String, PurchaseProposal> goods;
    private static Logger logger = Logger.getMyLogger("Supplier");

    public HashMap<String, PurchaseProposal> getGoods() {
        return goods;
    }

    public void setGoods(HashMap<String, PurchaseProposal> goods) {
        this.goods = goods;
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length == 0) {
            goods = DataGenerator.getRandomGoodsTable(this.getAID());
        }
        else {
            try {
                goods = (HashMap<String, PurchaseProposal>) args[0];
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, "Class Cast Exception by Supplier " + this.getAID().getName() + " creation");
                System.err.println("Class Cast Exception by Supplier " + this.getAID().getName() + " creation");

                goods = DataGenerator.getRandomGoodsTable(this.getAID());
            }
        }
        // Register the supplier service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("general-supplier");
        sd.setName("Goods Supplier");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Add the behaviour serving queries from customer agents
        addBehaviour(new OfferRequestsServer());
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Map<String, GoodNeed> goodsRequest = new JSONDeserializer<Map<String, GoodNeed>>().deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();
                HashMap<String, PurchaseProposal> requestedGoods = new HashMap<String, PurchaseProposal>();

                for (Map.Entry<String, GoodNeed> good : goodsRequest.entrySet()){
                    String goodName = good.getKey();
                    if (goods.containsKey(goodName)) {
                        requestedGoods.put(goodName, goods.get(goodName));
                    }
                }

                if (requestedGoods.size() != 0) {
                    // The requested goods are available for sale. Reply with the info
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(new JSONSerializer().serialize(requestedGoods));
                }
                else {
                    // The requested book is NOT available for sale.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }  // End of inner class OfferRequestsServer
}
