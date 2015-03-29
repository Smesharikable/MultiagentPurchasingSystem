package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.defaultvalues.DataGenerator;

import java.util.List;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private List<GoodNeed> goodNeeds;
    private double money;
    private String goodNeedsJSON;
    private AID[] customerAgents;

    @Override
    protected void setup() {
        System.out.println("Hallo! Buyer-agent " + this.getAID().getName() + " is ready.");

        goodNeeds = DataGenerator.getRandomGoodNeeds();
        money = DataGenerator.getRandomMoneyAmount();
        goodNeedsJSON = new JSONSerializer().serialize(goodNeeds);



        addBehaviour(new SearchCustomers());
    }

    protected void takeDown() {
        System.out.println("Buyer-agent " + this.getAID().getName() + " terminating.");
    }

    private class SearchCustomers extends Behaviour {
        public void action() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription templateSD = new ServiceDescription();
            templateSD.setType("customer");
            template.addServices(templateSD);

            try {
                DFAgentDescription[] fe = DFService.search(this.myAgent, template);
                System.out.println("Found the following seller agents:");
                customerAgents = new AID[fe.length];

                for(int i = 0; i < fe.length; ++i) {
                    customerAgents[i] = fe[i].getName();
                    System.out.println(customerAgents[i].getName());
                }
            } catch (FIPAException var5) {
                var5.printStackTrace();
            }

            this.myAgent.addBehaviour(new ChooseCustomer());
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    private class ChooseCustomer extends Behaviour {
        private MessageTemplate mt;
        private int step;
        private double minPrice = 0;
        private AID minPriceCustomer = null;
        private int repliesCnt;

        public ChooseCustomer() {
            repliesCnt = 0;
            this.step = 0;
        }

        @Override
        public void action() {
            ACLMessage reply;

            switch (step) {
            case 0:
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (AID customer : customerAgents) {
                    //TODO: check purchase date and need time

                    cfp.addReceiver(customer);
                }

                cfp.setContent(goodNeedsJSON);
                cfp.setConversationId("participation");
                cfp.setReplyWith("cfp" + System.currentTimeMillis());

                myAgent.send(cfp);
                mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participation"),
                        MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                step = 1;
                break;
            case 1:
                reply = this.myAgent.receive(this.mt);
                if(reply != null) {
                    if(reply.getPerformative() == ACLMessage.PROPOSE) {
                        //TODO: real prices check
                        double price = new JSONDeserializer<PurchaseInfo>().deserialize(reply.getContent()).getGoodsPrice().get(goodNeeds.get(0).getName());

                        if(minPriceCustomer == null || price < minPrice) {
                            minPrice = price;
                            minPriceCustomer = reply.getSender();
                        }
                    }

                    ++this.repliesCnt;
                    if(this.repliesCnt >= customerAgents.length) {
                        this.step = 2;
                    }
                } else {
                    this.block();
                }
                break;
            case 2:
                ACLMessage participate = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                participate.addReceiver(minPriceCustomer);
                participate.setContent(goodNeedsJSON);
                participate.setConversationId("participation");
                participate.setReplyWith("paticiper " + System.currentTimeMillis());
                myAgent.send(participate);
                break;
            }
        }

        @Override
        public boolean done() {
            return this.step == 2 && this.minPriceCustomer == null || this.step == 2;
        }
    }
}