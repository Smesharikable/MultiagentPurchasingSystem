package study.masystems.purchasingsystem;

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
import study.masystems.purchasingsystem.defaultvalues.DefaultGoods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Product supplier.
 */
public class Supplier extends Agent {

    private HashMap<String, GoodInfo> goods;

    @Override
    protected void setup() {
        goods = DefaultGoods.getRandomGoodsTable();

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
                List<String> goodsRequest = new JSONDeserializer<ArrayList<String>>().deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();
                HashMap<String, GoodInfo> requestedGoods = new HashMap<String, GoodInfo>();

                for (String good : goodsRequest){
                    if (goods.containsKey(good)) {
                        requestedGoods.put(good, goods.get(good));
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
