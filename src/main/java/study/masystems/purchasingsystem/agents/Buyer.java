package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.ParallelBehaviour;
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
import java.util.logging.Level;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private long WAIT_FOR_CUSTOMER_REPLIES_PERIOD_MS = 5000;
    private long WAIT_FOR_CUSTOMERS = 5000;
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<>();
    private JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");

    private Map<String, GoodNeed> goodNeeds;
    private double money;
    private HashMap<AID, String> customerAgents = new HashMap<>();
    private ProposalTable proposalTable = new ProposalTable();

    private static final int SUCCESS = 0;
    private static final int FAIL = 1;
    private static final int ABORT = 2;

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

        SequentialBehaviour buyerBehaviour = createBuyerBehaviour(WAIT_FOR_CUSTOMERS, getGoodNeeds().keySet());
        addBehaviour(buyerBehaviour);
    }

    protected void takeDown() {
        System.out.println("Buyer-agent " + this.getAID().getName() + " terminating.");
    }

    private BuyerBehaviour createBuyerBehaviour(long waitForCustomers,  Set<String> goods) {
        BuyerBehaviour buyerBehaviour = new BuyerBehaviour();
        buyerBehaviour.addSubBehaviour(new SearchCustomers(this, waitForCustomers));
        buyerBehaviour.addSubBehaviour(new ChooseCustomer(WAIT_FOR_CUSTOMER_REPLIES_PERIOD_MS, goods));
        buyerBehaviour.addSubBehaviour(new AcceptProposals());
        return buyerBehaviour;
    }

    private class BuyerBehaviour extends SequentialBehaviour {
        @Override
        protected void scheduleNext(boolean currentDone, int currentResult) {
            super.scheduleNext(currentDone, currentResult);
            if (currentDone) {
                switch (currentResult) {
                    case FAIL:
                        reset();
                        logger.log(Level.INFO, String.format("%s reset Buyer behaviour", getLocalName()));
                        break;
                    case ABORT:
                        doDelete();
                        break;
                }
            }
        }
    }

    private class SearchCustomers extends WakerBehaviour {
        private int status = SUCCESS;

        public SearchCustomers(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        public void reset(long timeout) {
            super.reset(timeout);
            status = SUCCESS;
        }

        @Override
        public void reset() {
            super.reset();
            status = SUCCESS;
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
                logger.log(Level.INFO, String.format("Buyer %s found the following seller agents:", myAgent.getLocalName()));

                for (DFAgentDescription aFe : fe) {
                    String purchaseName = "";

                    Iterator allServices = aFe.getAllServices();
                    if (!allServices.hasNext()) {
                        logger.log(Logger.WARNING, "Cannot find services by " + aFe.getName());
                    }

                    purchaseName = ((ServiceDescription) allServices.next()).getName();

                    customerAgents.put(aFe.getName(), purchaseName);
                    logger.log(Level.INFO, aFe.getName() + " purchase " + purchaseName);
                }

                if (fe.length == 0) {
                    status = FAIL;
                }
            } catch (FIPAException var5) {
                logger.log(Level.SEVERE, var5.toString());
                doDelete();
            }
        }

        @Override
        public int onEnd() {
            return status;
        }
    }

    private class ChooseCustomer extends Behaviour {
        private MessageTemplate mt;
        private int step = 0;
        private int repliesCnt = 0;
        private long period = 0;
        private long endTime = 0;
        private String goodNeedsJSON;

        public ChooseCustomer(long period, Set<String> goods) {
            this.period = period;
            Map<String, GoodNeed> currentNeeds = new HashMap<>();
            goods.forEach(good -> currentNeeds.put(good, goodNeeds.get(good)));
            this.goodNeedsJSON = jsonSerializer.serialize(goodNeeds);
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
                    }
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

    private class AcceptProposals extends Behaviour {

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

            // Add behaviour for each purchase.
            ParallelBehaviour joinThePurchases = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
            purchases.forEach((customer, demand) -> {
                joinThePurchases.addSubBehaviour(new ParticipateInPurchase(customer, demand));
            });
            addBehaviour(joinThePurchases);
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    private class ParticipateInPurchase extends SequentialBehaviour {
        private final AID customer;
        private final Demand demand;

        public ParticipateInPurchase(AID customer, Demand demand) {
            super();
            this.customer = customer;
            this.demand = demand;
        }

        @Override
        public void onStart() {
            super.onStart();
            this.addSubBehaviour(new JoinThePurchase(customer, demand));
            MessageTemplate mt = MessageTemplate.MatchConversationId(demand.getPurchaseName());
            this.addSubBehaviour(new WaitForConfirmation(mt));
            //TODO: Add behaviours for delivery.
        }

        @Override
        protected void scheduleNext(boolean currentDone, int currentResult) {
            super.scheduleNext(currentDone, currentResult);
            if (currentDone) {
                switch (currentResult) {
                    case FAIL:
                        skipNext();
                        // Make another attempt to satisfy demand.
                        addBehaviour(createBuyerBehaviour(0, demand.getOrders().keySet()));
                        break;
                    case ABORT:
                        doDelete();
                        break;
                }
            }
        }
    }

    private class JoinThePurchase extends Behaviour {
        private final AID customer;
        private final Demand demand;
        private int step = 0;
        private MessageTemplate mt;
        private boolean replyReceived = false;
        private long period = 0;
        private long endTime = 0;

        private final int STEP_SEND = 0;
        private final int STEP_RECEIVE = 1;

        private int status = SUCCESS;

        /**
         * Send ACCEPT_PROPOSAL to customer and handle replies.
         * @param customer Purchase owner.
         * @param demand List of goods with quantities.
         */
        public JoinThePurchase(AID customer, Demand demand) {
            this.customer = customer;
            this.demand = demand;
        }

        @Override
        public void action() {
            switch (step) {
                case STEP_SEND:
                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.setConversationId(demand.getPurchaseName());
                    accept.setContent(jsonSerializer.serialize(demand));
                    accept.addReceiver(customer);
                    myAgent.send(accept);

                    mt = MessageTemplate.MatchConversationId(accept.getConversationId());
                    step ++;
                    break;
                case STEP_RECEIVE:
                    ACLMessage message = receive(mt);
                    if (message != null) {
                        final int performative = message.getPerformative();
                        switch (performative) {
                            case ACLMessage.AGREE:
                                break;
                            default:
                                //TODO: Reset global behaviour.
                                status = FAIL;
                                break;
                        }
                        replyReceived = true;
                    } else {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done() {
            return replyReceived;
        }

        @Override
        public int onEnd() {
            return status;
        }
    }

    private class WaitForConfirmation extends Behaviour {
        private final MessageTemplate mt;
        private boolean purchaseCompleted = false;
        private int status = SUCCESS;

        public WaitForConfirmation(MessageTemplate mt) {
            this.mt = mt;
        }

        @Override
        public void action() {
            ACLMessage message = receive(mt);
            if (mt != null) {
                final int performative = message.getPerformative();
                switch (performative) {
                    case ACLMessage.CONFIRM:
                        //TODO: Delivery
                        break;
                    case  ACLMessage.CANCEL:
                        //TODO: Restart global behaviour;
                        status = FAIL;
                        break;
                }
                purchaseCompleted = true;
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return purchaseCompleted;
        }

        @Override
        public int onEnd() {
            return status;
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