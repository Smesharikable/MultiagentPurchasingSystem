package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import javafx.util.Pair;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private long WAIT_FOR_CUSTOMER_REPLIES_PERIOD_MS = 5000;
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<>();
    private JSONSerializer jsonDemandSerializer = new JSONSerializer();

    private Map<String, GoodNeed> goodNeeds;
    private String goodNeedsJSON;
    private double money;
    private AID[] customerAgents;
    private ProposalTable proposalTable = new ProposalTable();

    private static Logger logger = Logger.getMyLogger("Buyer");

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

        //Check whether an agent was read from file or created manually
        //If read, then parse args.
        Object[] args = getArguments();
        if (args == null || args.length == 0) {
            goodNeeds = DataGenerator.getRandomGoodNeeds();
            money = DataGenerator.getRandomMoneyAmount();
        }
        else {
            try {
                goodNeeds = (Map<String, GoodNeed>) args[0];
                money = (Integer) args[1];
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, "Class Cast Exception by Buyer " + this.getAID().getName() + " creation");

                goodNeeds = DataGenerator.getRandomGoodNeeds();
                money = DataGenerator.getRandomMoneyAmount();
            }
        }
        goodNeedsJSON = new JSONSerializer().serialize(goodNeeds);

        SequentialBehaviour buyerBehaviour = new SequentialBehaviour();
        buyerBehaviour.addSubBehaviour(new SearchCustomers());
        buyerBehaviour.addSubBehaviour(new ChooseCustomer(WAIT_FOR_CUSTOMER_REPLIES_PERIOD_MS));
        buyerBehaviour.addSubBehaviour(new ParticipateInPurchases());

        addBehaviour(buyerBehaviour);
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
        }
    }

    private class ChooseCustomer extends Behaviour {
        private MessageTemplate mt;
        private int step;
        private int repliesCnt;
        private long endTime;

        public ChooseCustomer(long period) {
            repliesCnt = 0;
            step = 0;
            endTime = System.currentTimeMillis() + period;
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
                //TODO: use ontology?
                cfp.setConversationId("participation");
                cfp.setReplyWith("cfp" + System.currentTimeMillis());

                myAgent.send(cfp);
                mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participation"),
                        MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                step = 1;
                break;
            case 1:
                reply = myAgent.receive(mt);
                if(reply != null) {
                    if(reply.getPerformative() == ACLMessage.PROPOSE) {
                        //TODO: real prices check
                        PurchaseInfo purchaseInfo = jsonDeserializer.deserialize(reply.getContent());
                        Map<String, Double> prices = purchaseInfo.getGoodsPrice();

                        for (Map.Entry<String, Double> entry: prices.entrySet()) {
                            String name = entry.getKey();
                            if (purchaseInfo.getDeliveryPeriodDays() < goodNeeds.get(name).getDeliveryPeriodDays()) {
                                proposalTable.addCustomerProposal(reply.getSender(), name, entry.getValue(), reply);
                            }
                        }
                    } // TODO: add for REFUSE?

                    repliesCnt++;
                } else {
                    this.block();
                }
                break;
            }
        }

        @Override
        public boolean done() {
            return (this.repliesCnt >= customerAgents.length) || (endTime <= System.currentTimeMillis());
        }
    }

    private class ParticipateInPurchases extends Behaviour {

        @Override
        public void action() {
            // Accept chosen proposals.
            Set<Map.Entry<String, ProposalTable.CustomerProposal>> proposals = proposalTable.getEntrySet();
            proposals.forEach(entry -> {
                String good = entry.getKey();
                GoodNeed goodNeed = goodNeeds.get(good);
                Pair<String, Integer> demand = new Pair<>(good, goodNeed.getQuantity());

                ProposalTable.CustomerProposal proposal = entry.getValue();
                ACLMessage reply = proposal.getMessage().createReply();
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                reply.setContent(jsonDemandSerializer.serialize(demand));

                myAgent.send(reply);
            });
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    private class ProposalTable {
        private Map<String, CustomerProposal> proposalMap = new HashMap<>();

        public ProposalTable() {
        }

        public void addCustomerProposal(AID customer, String name, double cost, ACLMessage message) {
            CustomerProposal customerProposal = proposalMap.get(name);
            if (customerProposal == null) {
                proposalMap.put(name, new CustomerProposal(customer, cost, message));
                return;
            }

            if (customerProposal.cost > cost) {
                proposalMap.put(name, new CustomerProposal(customer, cost, message));
            }
        }

        public Set<Map.Entry<String, CustomerProposal>> getEntrySet() {
            return proposalMap.entrySet();
        }

        private class CustomerProposal {
            private AID customer;
            private double cost;
            private ACLMessage message;

            public CustomerProposal(AID customer, double cost, ACLMessage message) {
                this.customer = customer;
                this.cost = cost;
                this.message = message;
            }

            public AID getCustomer() {
                return customer;
            }

            public double getCost() {
                return cost;
            }

            public ACLMessage getMessage() {
                return message;
            }
        }
    }


}