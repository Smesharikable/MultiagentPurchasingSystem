package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import jade.util.leap.Iterator;
import study.masystems.purchasingsystem.Demand;
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
    private long WAIT_FOR_CUSTOMERS = 5000;
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<>();
    private JSONSerializer jsonDemandSerializer = new JSONSerializer();

    private Map<String, GoodNeed> goodNeeds;
    private String goodNeedsJSON;
    private double money;
    private HashMap<AID, String> customerAgents;
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
        goodNeedsJSON = new JSONSerializer().exclude("*.class").serialize(goodNeeds);

        SequentialBehaviour buyerBehaviour = new SequentialBehaviour();
        buyerBehaviour.addSubBehaviour(new SearchCustomers(this, WAIT_FOR_CUSTOMERS));
        buyerBehaviour.addSubBehaviour(new ChooseCustomer(WAIT_FOR_CUSTOMER_REPLIES_PERIOD_MS));
        buyerBehaviour.addSubBehaviour(new ParticipateInPurchases());

        addBehaviour(buyerBehaviour);
    }

    protected void takeDown() {
        System.out.println("Buyer-agent " + this.getAID().getName() + " terminating.");
    }

    private class SearchCustomers extends WakerBehaviour {
        public SearchCustomers(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription templateSD = new ServiceDescription();
            templateSD.setType("customer");
            template.addServices(templateSD);

            try {
                DFAgentDescription[] fe = DFService.search(myAgent, template);
                System.out.println("Buyer found the following seller agents:");
                customerAgents = new HashMap<>();

                for (DFAgentDescription aFe : fe) {
                    String purchaseName = "";

                    Iterator allServices = aFe.getAllServices();
                    if (!allServices.hasNext()) {
                        logger.log(Logger.WARNING, "Cannot find services by " + aFe.getName());
                    }

                    //while (allServices.hasNext()) {
                    purchaseName = ((ServiceDescription) allServices.next()).getName();
                    //  if (purchaseName)
                    //}

                    customerAgents.put(aFe.getName(), purchaseName);
                    System.out.println(aFe.getName() + " purchase " + purchaseName);
                }

                if (fe.length == 0) {
                    parent.reset();
                }
            } catch (FIPAException var5) {
                parent.reset();
                var5.printStackTrace();
            }
        }
    }

    private class ChooseCustomer extends Behaviour {
        private MessageTemplate mt;
        private int step = 0;
        private int repliesCnt = 0;
        private long period = 0;
        private long endTime = 0;

        public ChooseCustomer(long period) {
            this.period = period;
        }

        @Override
        public void onStart() {
            super.onStart();
            endTime = System.currentTimeMillis() + period;
        }

        @Override
        public void action() {
            ACLMessage reply;

            switch (step) {
            case 0:
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                //TODO: check purchase date and need time
                customerAgents.keySet().forEach(cfp::addReceiver);

                cfp.setContent(goodNeedsJSON);
                String convId = "participation" + hashCode() + "_" + System.currentTimeMillis();
                cfp.setConversationId(convId);
                cfp.setReplyWith("cfp" + "_" + System.currentTimeMillis());

                myAgent.send(cfp);
                mt = MessageTemplate.and(MessageTemplate.MatchInReplyTo(cfp.getReplyWith()),
                                         MessageTemplate.MatchConversationId(convId));
                step = 1;
                break;
            case 1:
                reply = myAgent.receive(mt);
                if(reply != null) {
                    if(reply.getPerformative() == ACLMessage.PROPOSE) {
                        //TODO: real prices check
                        PurchaseInfo purchaseInfo = jsonDeserializer.deserialize(reply.getContent(), PurchaseInfo.class);
                        Map<String, Double> prices = purchaseInfo.getGoodsPrice();

                        for (Map.Entry<String, Double> entry: prices.entrySet()) {
                            String name = entry.getKey();
                            if (purchaseInfo.getDeliveryPeriodDays() < goodNeeds.get(name).getDeliveryPeriodDays()) {
                                proposalTable.addCustomerProposal(reply.getSender(), name, entry.getValue());
                            }
                        }
                        repliesCnt++;
                    } // TODO: add for REFUSE?

                } else {
                    this.block();
                }
                break;
            }
        }

        @Override
        public boolean done() {
            return (this.repliesCnt >= customerAgents.size()) || (endTime <= System.currentTimeMillis());
        }
    }

    private class ParticipateInPurchases extends Behaviour {

        @Override
        public void action() {
            // Accept chosen proposals.
            System.out.println("Buyer accept proposal.");
            Map<AID, Demand> purchases = new HashMap<>();

            for (Map.Entry<String, ProposalTable.CustomerProposal> entry: proposalTable.getEntrySet()) {
                String good = entry.getKey();
                ProposalTable.CustomerProposal customerProposal = entry.getValue();
                AID customer = customerProposal.getCustomer();

                Demand demand = purchases.get(customer);
                if (demand == null) {
                    demand = new Demand(customerAgents.get(customer));
                    GoodNeed goodNeed = goodNeeds.get(good);
                    demand.put(good, goodNeed.getQuantity());
                    purchases.put(customer, demand);
                } else {
                    GoodNeed goodNeed = goodNeeds.get(good);
                    demand.put(good, goodNeed.getQuantity());
                }
            }

            purchases.forEach((customer, demand) -> {
                ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                accept.setConversationId(demand.getPurchaseName() + "_" + hashCode() + System.currentTimeMillis());
                accept.setContent(jsonDemandSerializer.exclude("*.class").serialize(demand));
                accept.addReceiver(customer);

                myAgent.send(accept);
            });

/*            Set<Map.Entry<String, ProposalTable.CustomerProposal>> proposals = proposalTable.getEntrySet();
            proposals.forEach(entry -> {
                String good = entry.getKey();
                GoodNeed goodNeed = goodNeeds.get(good);
                Demand demand = new Demand(good, goodNeed.getQuantity());

                ProposalTable.CustomerProposal proposal = entry.getValue();
                ACLMessage reply = proposal.getMessage().createReply();
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                reply.setContent(jsonDemandSerializer.exclude("*.class").serialize(demand));

                myAgent.send(reply);
            });*/
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    private class ProposalTable {
        private Map<String, CustomerProposal> proposalMap = new HashMap<>();

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

        public Set<Map.Entry<String, CustomerProposal>> getEntrySet() {
            return proposalMap.entrySet();
        }

        private class CustomerProposal {
            private AID customer;
            private double cost;

            public CustomerProposal(AID customer, double cost) {
                this.customer = customer;
                this.cost = cost;
            }

            public AID getCustomer() {
                return customer;
            }

            public double getCost() {
                return cost;
            }
        }
    }
}