package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<PurchaseInfo>();
    private static Logger logger = Logger.getMyLogger("Buyer");

    private Map<String, GoodNeed> goodNeeds;
    private double money;
    private String goodNeedsJSON;
    private AID[] customerAgents;

    private ProposalTable proposalTable = new ProposalTable();

    public Map<String, GoodNeed> getGoodNeeds() {
        return goodNeeds;
    }

    public void setGoodNeeds(Map<String, GoodNeed> goodNeeds) {
        this.goodNeeds = goodNeeds;
    }

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    @Override
    protected void setup() {
        System.out.println("Hallo! Buyer-agent " + this.getAID().getName() + " is ready.");

        Object[] args = getArguments();
        if (args == null || args.length == 0) {
            System.out.println("RANDOOOM");
            goodNeeds = DataGenerator.getRandomGoodNeeds();
            money = DataGenerator.getRandomMoneyAmount();
        }
        else {
            try {
                goodNeeds = (Map<String, GoodNeed>) args[0];
                System.out.println(goodNeeds);
                money = (Integer) args[1];
                System.out.println(money);
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, "Class Cast Exception by Buyer " + this.getAID().getName() + " creation");
                System.err.println("Class Cast Exception by Buyer " + this.getAID().getName() + " creation");

                goodNeeds = DataGenerator.getRandomGoodNeeds();
                money = DataGenerator.getRandomMoneyAmount();
            }
        }
        goodNeedsJSON = new JSONSerializer().serialize(goodNeeds);

        addBehaviour(new SearchCustomers());
    }

    protected void takeDown() {
        System.out.println("Buyer-agent " + this.getAID().getName() + " terminating.");
    }

    private class SearchCustomers extends OneShotBehaviour {
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
    }

    private class ChooseCustomer extends Behaviour {
        private MessageTemplate mt;
        private int step;
        private int repliesCnt;
        private boolean allReceived = false;

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
                        PurchaseInfo purchaseInfo = jsonDeserializer.deserialize(reply.getContent());
                        Map<String, Double> prices = purchaseInfo.getGoodsPrice();

                        for (Map.Entry<String, Double> entry: prices.entrySet()) {
                            String name = entry.getKey();
                            if (purchaseInfo.getDeliveryPeriod() < goodNeeds.get(name).getDeliveryPeriodDays()) {
                                proposalTable.addCustomerProposal(reply.getSender(), name, entry.getValue());
                            }
                        }

                    }

                    repliesCnt++;
                    allReceived = (this.repliesCnt >= customerAgents.length);
                } else {
                    this.block();
                }
                break;
            }
        }

        @Override
        public boolean done() {
            return allReceived;
        }
    }

    private class ProposalTable {
        private Map<String, CustomerProposal> proposalMap = new HashMap<String, CustomerProposal>();

        public ProposalTable() {
        }

        public void addCustomerProposal(AID customer, String name, double cost) {
            CustomerProposal customerProposal = proposalMap.get(name);
            if (customerProposal == null) {
                proposalMap.put(name, new CustomerProposal(customer, cost));
                return;
            }

            if (customerProposal.cost > cost) {
                proposalMap.put(name, new CustomerProposal(customer, cost));
            }
        }

    }

    private class CustomerProposal {
        private AID customer;
        private double cost;

        public CustomerProposal(AID customer, double cost) {
            this.customer = customer;
            this.cost = cost;
        }
    }
}