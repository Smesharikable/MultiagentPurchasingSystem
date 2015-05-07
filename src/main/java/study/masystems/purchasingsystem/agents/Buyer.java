package study.masystems.purchasingsystem.agents;


import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private long WAIT_FOR_CUSTOMER_REPLIES_PERIOD = 5000;
    private long WAIT_FOR_CUSTOMERS_PERIOD = 5000;
    private long CHECK_NEEDS_PERIOD = 5000;
    private long ACTIVITY_PERIOD = 30000;
    private int MAX_SEARCH_CUSTOMER_ITERATION = 3;
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<>();
    private JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");

    private Map<String, GoodNeed> goodNeeds;
    private double money;
    private HashMap<AID, String> customerAgents = new HashMap<>();
    private ProposalTable proposalTable = new ProposalTable();
    private Set<String> restGoods = new HashSet<>();

    private boolean isActive = true;

    private static final int SUCCESS = 0;
    private static final int FAIL = 1;
    private static final int ABORT = 2;

    private static Logger logger = Logger.getMyLogger("Buyer");

    public Map<String, GoodNeed> getGoodNeeds() {
        return goodNeeds;
    }

    @Override
    protected void setup() {
        //Check whether an agent was read from file or created manually
        //If read, then parse args.
        Object[] args = getArguments();
        if (args == null || args.length == 0) {
            goodNeeds = DataGenerator.getRandomGoodNeeds();
            money = DataGenerator.getRandomMoneyAmount();
        } else {
            try {
                goodNeeds = (Map<String, GoodNeed>) args[0];
                money = (Integer) args[1];
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, String.format("Class Cast Exception by Buyer %s creation", getLocalName()));
                goodNeeds = DataGenerator.getRandomGoodNeeds();
                money = DataGenerator.getRandomMoneyAmount();
            }
        }

        restGoods.addAll(goodNeeds.keySet());

        addBehaviour(new BuyerBehaviour(this, WAIT_FOR_CUSTOMERS_PERIOD, WAIT_FOR_CUSTOMER_REPLIES_PERIOD, restGoods));

        // Set flag when time for customer search is over.
        addBehaviour(new WakerBehaviour(this, ACTIVITY_PERIOD) {
            @Override
            protected void onWake() {
                super.onWake();
                isActive = false;
                logger.log(Level.INFO, String.format("Buyer %s is inactive.", getLocalName()));
            }
        });
    }

    protected void takeDown() {
        logger.log(Level.INFO, String.format("Buyer-agent %s terminating.", getLocalName()));
    }

    private class MakeAnotherAttempt extends WakerBehaviour {

        public MakeAnotherAttempt(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            if (isActive) {
                if (!restGoods.isEmpty()) {
                    addBehaviour(new BuyerBehaviour(myAgent, WAIT_FOR_CUSTOMERS_PERIOD,
                            WAIT_FOR_CUSTOMER_REPLIES_PERIOD, restGoods));
//                    restGoods.clear();
                } else {
                    addBehaviour(new MakeAnotherAttempt(myAgent, CHECK_NEEDS_PERIOD));
                }
            } else {
                if (JoinThePurchasesBehaviour.getInstanceCount() == 0) {
                    // We purchase all that we can.
                    doDelete();
                }
                // Stop attempting to find a customer.
            }
        }
    }

    private class BuyerBehaviour extends SequentialBehaviour {
        public BuyerBehaviour(Agent a, long waitForCustomers, long waitForCustomerReplies, Set<String> goods) {
            super(a);
            this.addSubBehaviour(new SearchCustomers(myAgent, waitForCustomers));
            this.addSubBehaviour(new ChooseCustomer(waitForCustomerReplies, goods));
            this.addSubBehaviour(new AcceptProposals());
        }

        @Override
        protected void scheduleNext(boolean currentDone, int currentResult) {
            super.scheduleNext(currentDone, currentResult);
            if (currentDone) {
                switch (currentResult) {
                    case FAIL:
                        reset();
                        logger.log(Level.INFO, String.format("%s reset Buyer behaviour", getLocalName()));
                        break;
                    case ABORT: // We can't find any customer.
                        skipNext();
                        addBehaviour(new MakeAnotherAttempt(myAgent, CHECK_NEEDS_PERIOD));
                        break;
                }
            }
        }
    }

    private class SearchCustomers extends WakerBehaviour {
        private int status = SUCCESS;
        private int count = 0;

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
            count++;
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription templateSD = new ServiceDescription();
            templateSD.setType("customer");
            template.addServices(templateSD);

            try {
                DFAgentDescription[] fe = DFService.search(myAgent, template);
                logger.log(Level.INFO, String.format("Buyer %s found the following seller agents:", myAgent.getLocalName()));

                for (DFAgentDescription aFe : fe) {
                    String purchaseName;

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
                if (count >= MAX_SEARCH_CUSTOMER_ITERATION) {
                    status = ABORT;
                }
            } catch (FIPAException var5) {
                logger.log(Level.SEVERE, var5.toString());
                status = ABORT;
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
            this.goodNeedsJSON = jsonSerializer.serialize(currentNeeds);
        }

        @Override
        public void onStart() {
            super.onStart();
            endTime = System.currentTimeMillis() + period;
        }

        @Override
        public void reset() {
            super.reset();
            repliesCnt = 0;
            step = 0;
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
            logger.log(Level.INFO, String.format("%s accept proposal.", getLocalName()));
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
                restGoods.removeAll(demand.getOrders().keySet());
            }
            customerAgents.clear();

            // Add behaviour for each purchase.
            ParallelBehaviour joinThePurchases = new JoinThePurchasesBehaviour(ParallelBehaviour.WHEN_ALL);
            purchases.forEach((customer, demand) ->
                    joinThePurchases.addSubBehaviour(new ParticipateInPurchase(customer, demand))
            );
            SequentialBehaviour purchase = new SequentialBehaviour();
            purchase.addSubBehaviour(joinThePurchases);
            purchase.addSubBehaviour(new CheckNeeds());

            addBehaviour(purchase);

            addBehaviour(new MakeAnotherAttempt(myAgent, CHECK_NEEDS_PERIOD));
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    private static class JoinThePurchasesBehaviour extends ParallelBehaviour {
        static int instanceCount = 0;

        public JoinThePurchasesBehaviour() {
            instanceCount++;
        }

        public JoinThePurchasesBehaviour(int endCondition) {
            super(endCondition);
            instanceCount++;
        }

        public JoinThePurchasesBehaviour(Agent a, int endCondition) {
            super(a, endCondition);
            instanceCount++;
        }

        public static int getInstanceCount() {
            return instanceCount;
        }

        @Override
        public int onEnd() {
            instanceCount--;
            return super.onEnd();
        }
    }

    private class ParticipateInPurchase extends SequentialBehaviour {
        private final AID customer;
        private final Demand demand;

        public ParticipateInPurchase(AID customer, Demand demand) {
            super();
            this.customer = customer;
            this.demand = demand;
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
                        restGoods.addAll(demand.getOrders().keySet());
                        break;
                    case ABORT:
                        logger.log(Level.SEVERE, "Participation in purchase has been aborted.");
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
        public void reset() {
            super.reset();
            status = SUCCESS;
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
        public void reset() {
            super.reset();
            status = SUCCESS;
            purchaseCompleted = false;
        }

        @Override
        public void action() {
            ACLMessage message = receive(mt);
            if (message != null) {
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

    /**
     * Delete the buyer after purchase, if there no more needs.
     */
    private class CheckNeeds extends OneShotBehaviour {
        @Override
        public void action() {
            if (goodNeeds.isEmpty()) {
                doDelete();
            }
        }
    }

    private class   ProposalTable {
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