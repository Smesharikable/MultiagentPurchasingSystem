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
import study.masystems.purchasingsystem.GoodInformation;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseProposal;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Product supplier.
 */
public class Supplier extends Agent {
    private JSONSerializer jsonSerializer = new JSONSerializer();
    private JSONDeserializer<Map<String, GoodNeed>> customerProposeDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Map<String, Integer>> orderDeserializer = new JSONDeserializer<>();
    private HashMap<String, GoodInformation> goods;

    public HashMap<String, GoodInformation> getGoods() {
        return goods;
    }

    public void setGoods(HashMap<String, GoodInformation> goods) {
        this.goods = goods;
    }

    private static Logger logger = Logger.getMyLogger(Supplier.class.getName());

    @Override
    protected void setup() {
        //Check whether an agent was read from file or created manually
        //If read, then parse args.
        Object[] args = getArguments();
        if (args == null || args.length == 0) {
            goods = DataGenerator.getRandomGoodsTable();
        }
        else {
            try {
                goods = (HashMap<String, GoodInformation>) args[0];
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, "Class Cast Exception by Supplier " + this.getAID().getName() + " creation");

                goods = DataGenerator.getRandomGoodsTable();
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
        addBehaviour(new HandleOrdersBehaviour());
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Map<String, GoodNeed> goodsRequest = customerProposeDeserializer.use("values", GoodNeed.class).deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();
                HashMap<String, PurchaseProposal> requestedGoods = new HashMap<>();

                for (Map.Entry<String, GoodNeed> good : goodsRequest.entrySet()){
                    String goodName = good.getKey();
                    if (goods.containsKey(goodName)) {
                        requestedGoods.put(goodName, new PurchaseProposal(myAgent.getAID(), goods.get(goodName)));
                    }
                }

                if (requestedGoods.size() != 0) {
                    // The requested goods are available for sale. Reply with the info
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(jsonSerializer.exclude("*.class").serialize(requestedGoods));
                } else {
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
    }

    private class HandleOrdersBehaviour extends CyclicBehaviour {
        private MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
        @Override
        public void action() {
            ACLMessage orderMessage = myAgent.receive(mt);
            if (orderMessage != null) {
                ACLMessage reply = orderMessage.createReply();
                final Map<String, Integer> order = orderDeserializer.deserialize(orderMessage.getContent());
                final boolean isComplete;
                try {
                    isComplete = checkGoodInformation(order);
                    if (isComplete) {
                        reply.setPerformative(ACLMessage.CONFIRM);
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("not complete order");
                    }
                } catch (NoSuchElementException e) {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("no required goods");
                }
                send(reply);
            } else {
                block();
            }
        }
    }

    private boolean checkGoodInformation(Map<String, Integer> order) throws NoSuchElementException {
        for(Map.Entry<String, Integer> entry : order.entrySet()) {
            String good = entry.getKey();
            GoodInformation goodInformation = goods.get(good);
            if (goodInformation == null) {
                throw new NoSuchElementException();
            }
            Integer quantity = entry.getValue();
            if (goodInformation.getMinimalQuantity() > quantity) {
                return false;
            }
        }
        return true;
    }
}
