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
import jade.lang.acl.UnreadableException;
import jade.util.Logger;
import jade.util.leap.Iterator;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import study.masystems.purchasingsystem.Demand;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.jgrapht.BuyerGraphPath;
import study.masystems.purchasingsystem.jgrapht.WeightedEdge;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;

/**
 * Purchase participant, that wants to buy some goods.
 */
public class Buyer extends Agent {
    private long WAIT_FOR_CUSTOMER_REPLIES_PERIOD = 5000;
    private long WAIT_FOR_CUSTOMERS_PERIOD = 5000;
    private long CHECK_NEEDS_PERIOD = 5000;
    private long ACTIVITY_PERIOD = 30000;
    private long PROPAGATION_PERIOD = 1000;
    private long DELIVERY_PERIOD = 5000;
    private int MAX_SEARCH_CUSTOMER_ITERATION = 3;
    private int WAIT_FOR_DELIVERY_FACTOR = 1000;
    private JSONDeserializer<PurchaseInfo> jsonDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Set<AID>> agentsDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Integer> positionDeserialize = new JSONDeserializer<>();
    private JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");

    private Map<String, GoodNeed> goodNeeds;
    private double money;
    private FloydWarshallShortestPaths<Integer, WeightedEdge> cityPaths;
    private BuyerGraphPath<Integer, WeightedEdge> path;
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
                cityPaths = (FloydWarshallShortestPaths<Integer, WeightedEdge>) args[0];
                path = new BuyerGraphPath<>((GraphPath<Integer, WeightedEdge>) args[1]);
                goodNeeds = (Map<String, GoodNeed>) args[2];
                money = (Integer) args[3];
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
                } else {
                    addBehaviour(new MakeAnotherAttempt(myAgent, CHECK_NEEDS_PERIOD));
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
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            //TODO: real prices check
                            PurchaseInfo purchaseInfo = jsonDeserializer.deserialize(reply.getContent(), PurchaseInfo.class);
                            Map<String, Double> prices = purchaseInfo.getGoodsPrice();
                            final Map<String, Integer> goodsRest = purchaseInfo.getGoodsRest();

                            for (Map.Entry<String, Double> entry : prices.entrySet()) {
                                String name = entry.getKey();
                                if (purchaseInfo.getDeliveryPeriodDays() < goodNeeds.get(name).getDeliveryPeriodDays()) {
                                    proposalTable.addCustomerProposal(reply.getSender(), name, entry.getValue(), goodsRest.get(name));
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

            for (Map.Entry<String, ProposalTable.CustomerProposal> entry : proposalTable.getEntrySet()) {
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
        private final Demand demand;

        public ParticipateInPurchase(AID customer, Demand demand) {
            super();
            this.demand = demand;
            this.addSubBehaviour(new JoinThePurchase(customer, demand));
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId(demand.getPurchaseName()),
                    MessageTemplate.MatchSender(customer));
            this.addSubBehaviour(new WaitForConfirmation(mt, this));
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
         *
         * @param customer Purchase owner.
         * @param demand   List of goods with quantities.
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
                    step++;
                    break;
                case STEP_RECEIVE:
                    ACLMessage message = receive(mt);
                    if (message != null) {
                        final int performative = message.getPerformative();
                        switch (performative) {
                            case ACLMessage.AGREE:
                                break;
                            default:
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
        private final ParticipateInPurchase participateInPurchase;
        private boolean purchaseCompleted = false;
        private int status = SUCCESS;

        public WaitForConfirmation(MessageTemplate mt, ParticipateInPurchase participateInPurchase) {
            this.mt = mt;
            this.participateInPurchase = participateInPurchase;
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
//                        final Set<AID> buyers = agentsDeserializer.deserialize(message.getContent());
                        final Set<AID> buyers = new HashSet<>();
                        final Iterator allReceiver = message.getAllReceiver();
                        allReceiver.forEachRemaining(buyer -> buyers.add((AID) buyer));
                        final Integer position = positionDeserialize.deserialize(message.getUserDefinedParameter("position"));
                        participateInPurchase.addSubBehaviour(
                                new DeliveryBehaviour(myAgent, message.getSender(), buyers, position, message.getConversationId()));
                        break;
                    case ACLMessage.CANCEL:
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
            if (!isActive && JoinThePurchasesBehaviour.getInstanceCount() == 0) {
                doDelete();
            }
        }
    }

    private class DeliveryBehaviour extends SequentialBehaviour {
        final private AID customer;
        final private Set<AID> otherBuyers;
        final private String deliveryConversationID;

        private Set<AID> destinations = new HashSet<>();
        private Map<AID, Set<AID>> destinationTable = new HashMap<>();
        private HashSet<AID> extendedDestination = new HashSet<>();

        public DeliveryBehaviour(Agent a, AID customer, Set<AID> buyers, Integer customerPosition, String baseConversationID) {
            super(a);
            buyers.remove(myAgent.getAID());

            this.customer = customer;
            this.otherBuyers = buyers;
            this.deliveryConversationID = baseConversationID + "_delivery";

            //TODO: Add behaviours
            DeliveryConfig deliveryConfig = new DeliveryConfig();
            addSubBehaviour(new WaitForDeliveryProposal(this, customerPosition, deliveryConfig));
            addSubBehaviour(new ConfigureDelivery(this, deliveryConfig));
        }

        public AID getCustomer() {
            return customer;
        }

        public Set<AID> getOtherBuyers() {
            return otherBuyers;
        }

        public String getDeliveryConversationID() {
            return deliveryConversationID;
        }

        public Set<AID> getDestinations() {
            return destinations;
        }

        public Map<AID, Set<AID>> getDestinationTable() {
            return destinationTable;
        }

        public HashSet<AID> getExtendedDestination() {
            return extendedDestination;
        }

        public void addDestinations(AID buyer) {
            destinations.add(buyer);
        }

        public void addToTable(AID intermediary, AID destination) {
            Set<AID> aids = destinationTable.get(intermediary);
            if (aids == null) {
                aids = new HashSet<>();
                aids.add(destination);
                destinationTable.put(intermediary, aids);
            } else {
                aids.add(destination);
            }
        }

        public void addToExtendedDestination(AID buyer) {
            extendedDestination.add(buyer);
        }
    }

    private class DeliveryConfig {
        AID deliveryAgent;
        Date replyByDate;

        public DeliveryConfig() {
        }

        public AID getDeliveryAgent() {
            return deliveryAgent;
        }

        public void setDeliveryAgent(AID deliveryAgent) {
            this.deliveryAgent = deliveryAgent;
        }

        public Date getReplyByDate() {
            return replyByDate;
        }

        public void setReplyByDate(Date replyByDate) {
            this.replyByDate = replyByDate;
        }
    }

    private class ConfigureDelivery extends OneShotBehaviour {
        DeliveryBehaviour deliveryBehaviour;
        DeliveryConfig deliveryConfig;
        public ConfigureDelivery(DeliveryBehaviour deliveryBehaviour, DeliveryConfig deliveryConfig) {
            super();
            this.deliveryBehaviour = deliveryBehaviour;
            this.deliveryConfig = deliveryConfig;
        }

        @Override
        public void action() {
            final Date replyByDate = deliveryConfig.getReplyByDate();
            final AID deliveryAgent = deliveryConfig.getDeliveryAgent();
            final long delta = replyByDate.getTime() - System.currentTimeMillis();
            logger.log(Level.INFO, String.format("Delta %s, %d", getLocalName(), delta));
            if (delta > PROPAGATION_PERIOD) {
                deliveryBehaviour.addSubBehaviour(new PropagateProposal(deliveryBehaviour, replyByDate.getTime(), deliveryAgent));
            }
            deliveryBehaviour.addSubBehaviour(new ReceiveGoods(deliveryBehaviour, deliveryAgent));

        }
    }

    private class WaitForDeliveryProposal extends Behaviour {
        final private DeliveryBehaviour deliveryBehaviour;
        final private Set<AID> otherBuyers;
        final private DeliveryConfig deliveryConfig;
        final private double distance;
        private long endTime;
        private MessageTemplate messageTemplate;
        private AID lastCandidate;
        private AID deliveryAgent;
        private Date replyByDate;


        private int step = 0;

        public WaitForDeliveryProposal(DeliveryBehaviour deliveryBehaviour, Integer customerPosition, DeliveryConfig deliveryConfig) {
            this.deliveryBehaviour = deliveryBehaviour;
            this.otherBuyers = deliveryBehaviour.getOtherBuyers();
            this.deliveryConfig = deliveryConfig;

            final GraphPath<Integer, WeightedEdge> firstPart = cityPaths.getShortestPath(path.getStartVertex(), customerPosition);
            final GraphPath<Integer, WeightedEdge> secondPart = cityPaths.getShortestPath(customerPosition, path.getEndVertex());
            this.distance = firstPart.getWeight() + secondPart.getWeight() - path.getWeight();

            messageTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()),
                    MessageTemplate.MatchContent("delivery"));
        }

        @Override
        public void onStart() {
            super.onStart();
            endTime = System.currentTimeMillis() + calculateWaitingTime();
            step = 0;
            logger.log(Level.INFO, String.format("Delivery wait. %s, %d", getLocalName(), calculateWaitingTime()));
        }

        private long calculateWaitingTime() {
            return (long) (distance * WAIT_FOR_DELIVERY_FACTOR);
        }

        @Override
        public void action() {
            ACLMessage message = receive(messageTemplate);
            if (message != null) {
                final int performative = message.getPerformative();
                switch (step) {
                    //Handle proposals for delivery from other buyers.
                    case 0:
                        if (performative != ACLMessage.PROPOSE) {
                            break;
                        }
                        otherBuyers.remove(message.getSender());
                        final List<Integer> vertices = BuyerGraphPath.<Integer>deserializePath(
                                message.getUserDefinedParameter("path"));

                        // Check paths intersection.
                        boolean isAccepted = false;
                        final List<Integer> pathVertices = path.getVertices();
                        for (Integer vertex : vertices) {
                            if (pathVertices.contains(vertex)) {
                                acceptProposal(message);
                                isAccepted = true;
                                step = 1;
                                break;
                            }
                        }
                        if (isAccepted) {
                            break;
                        }

                        // Estimate gain.
                        double newWeight = Double.POSITIVE_INFINITY;
                        final Integer startVertex = path.getStartVertex();
                        final Integer endVertex = path.getEndVertex();

                        for (Integer vertex : vertices) {
                            final GraphPath<Integer, WeightedEdge> firstPart = cityPaths.getShortestPath(startVertex, vertex);
                            final GraphPath<Integer, WeightedEdge> secondPart = cityPaths.getShortestPath(vertex, endVertex);
                            final double currentWeight = firstPart.getWeight() + secondPart.getWeight();
                            if (currentWeight < newWeight) {
                                newWeight = currentWeight;
                            }
                        }

                        final double weight = path.getWeight();
                        double gain = (weight - newWeight) / weight;
                        if (gain > 0.1) {
                            acceptProposal(message);
                            step = 1;
                        } else {
                            rejectProposal(message);
                        }
                        break;
                    // Wait for confirmation.
                    case 1:
                        switch (performative) {
                            case ACLMessage.CONFIRM:
                                deliveryAgent = lastCandidate;
                                replyByDate = new Date(new Long(message.getUserDefinedParameter("time")));
                                break;
                            case ACLMessage.CANCEL:
                                step = 0;
                                break;
                            case ACLMessage.PROPOSE:
                                rejectProposal(message);
                                break;
                        }
                        break;
                }
            } else {
                block(1000);
            }
        }

        private void acceptProposal(ACLMessage message) {
            final ACLMessage reply = message.createReply();
            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            reply.addReplyTo(myAgent.getAID());
            send(reply);
            lastCandidate = message.getSender();
        }

        private void rejectProposal(ACLMessage message) {
            final ACLMessage reply = message.createReply();
            reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
            send(reply);
        }

        @Override
        public boolean done() {
            return (System.currentTimeMillis() > endTime) || (deliveryAgent != null);
        }

        @Override
        public int onEnd() {
            if (deliveryAgent == null) {
                deliveryAgent = deliveryBehaviour.getCustomer();
                ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                message.setConversationId(deliveryBehaviour.getDeliveryConversationID());
                long deliveryTime = System.currentTimeMillis() + DELIVERY_PERIOD + 1000;
                message.setContent(String.valueOf(deliveryTime));
                message.addReceiver(deliveryAgent);
                send(message);
                replyByDate = new Date(deliveryTime);
            }
            deliveryConfig.setDeliveryAgent(deliveryAgent);
            deliveryConfig.setReplyByDate(replyByDate);
            return super.onEnd();
        }
    }

    /**
     * Send proposal to other buyers. Wait for request from delivery agent.
     */
    private class PropagateProposal extends Behaviour {
        private final DeliveryBehaviour deliveryBehaviour;
        private final MessageTemplate acceptMT;
        private final MessageTemplate infoMT;
        private final long endTime;
        private final AID deliveryAgent;

        private int step = 0;

        public PropagateProposal(DeliveryBehaviour deliveryBehaviour, long endTime, AID deliveryAgent) {
            this.deliveryBehaviour = deliveryBehaviour;
            this.endTime = endTime;
            this.deliveryAgent = deliveryAgent;
            this.acceptMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()));
            this.infoMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()));
        }

        @Override
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
                    proposal.setConversationId(deliveryBehaviour.getDeliveryConversationID());
                    deliveryBehaviour.getOtherBuyers().forEach(proposal::addReceiver);
                    proposal.setContent("delivery");
                    proposal.addUserDefinedParameter("path", path.serializePath());
                    send(proposal);
                    step = 1;
                    break;
                case 1:
                    final ACLMessage acceptMsg = receive(acceptMT);
                    if (acceptMsg != null) {
                        deliveryBehaviour.addDestinations(acceptMsg.getSender());
                        final ACLMessage reply = acceptMsg.createReply();
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent("delivery");
                        reply.addUserDefinedParameter("time", String.valueOf(endTime));
                        send(reply);
                    } else {
                        block(1000);
                    }
                    break;
            }
        }

        @Override
        public boolean done() {
            return System.currentTimeMillis() >= endTime;
        }
    }

    private class ReceiveGoods extends Behaviour {
        private final DeliveryBehaviour deliveryBehaviour;
        private final AID deliveryAgent;
        private final MessageTemplate requestMT;
        private final MessageTemplate replyMT;
        private final MessageTemplate receiveGoodsMT;

        private ACLMessage requestReply;

        private int step = 0;
        private int received = 0;
        private int sendCount = 0;

        public ReceiveGoods(DeliveryBehaviour deliveryBehaviour, AID deliveryAgent) {
            this.deliveryBehaviour = deliveryBehaviour;
            this.deliveryAgent = deliveryAgent;
            this.requestMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()));
            this.replyMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE),
                    MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()));
            this.sendCount = deliveryBehaviour.getDestinations().size();
            this.receiveGoodsMT = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                    MessageTemplate.MatchConversationId(deliveryBehaviour.getDeliveryConversationID()));
        }

        @Override
        public void action() {
            switch (step) {
                case 0:
                    final ACLMessage request = receive(requestMT);
                    if (request != null) {
                        final Set<AID> destinations = deliveryBehaviour.getDestinations();
                        requestReply = request.createReply();
                        // Propagate request.
                        if (destinations.size() > 0) {
                            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
                            message.setConversationId(deliveryBehaviour.getDeliveryConversationID());
                            destinations.forEach(message::addReceiver);
                            send(message);
                            step = 1;
                        } else {
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 1:
                    // receive aids
                    final ACLMessage propagate = receive(replyMT);
                    if (propagate != null) {
                        try {
                            final HashSet<AID> contentObject = (HashSet<AID>) propagate.getContentObject();
                            final AID sender = propagate.getSender();
                            deliveryBehaviour.addToTable(getAID(), sender);
                            contentObject.forEach(aid -> {
                                deliveryBehaviour.addToTable(sender, aid);
                                deliveryBehaviour.addToExtendedDestination(aid);
                            });
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        }
                        received++;
                        if (received >= sendCount) {
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    // reply to request
                    final Set<AID> destinations = deliveryBehaviour.getDestinations();
                    final HashSet<AID> extendedDestination = deliveryBehaviour.getExtendedDestination();
                    extendedDestination.addAll(destinations);
                    requestReply.setPerformative(ACLMessage.PROPAGATE);
                    try {
                        requestReply.setContentObject(extendedDestination);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    send(requestReply);
                    step = 3;
                    break;
                case 3:
                    // receive goods
                    final ACLMessage goodsMsg = receive(receiveGoodsMT);
                    if (goodsMsg != null) {
                        try {
                            final Map<AID, Map<String, Integer>> goodsMap =
                                    (Map<AID, Map<String, Integer>>) goodsMsg.getContentObject();
                            deleteGoodNeeds(goodsMap.get(getAID()));

                            final Set<AID> aids = deliveryBehaviour.getDestinations();
                            final Map<AID, Set<AID>> destinationTable = deliveryBehaviour.getDestinationTable();
                            for (AID aid: aids) {
                                HashMap<AID, Map<String, Integer>> aidGoodsMap = new HashMap<>();
                                aidGoodsMap.put(aid, goodsMap.get(aid));
                                final Set<AID> receivers = destinationTable.get(aid);
                                if (receivers != null) {
                                    receivers.forEach(receiver -> aidGoodsMap.put(receiver, goodsMap.get(receiver)));
                                }

                                ACLMessage deliveryMsg = new ACLMessage(ACLMessage.CONFIRM);
                                deliveryMsg.setConversationId(deliveryBehaviour.getDeliveryConversationID());
                                deliveryMsg.setContentObject(aidGoodsMap);
//                                deliveryMsg.setContent("goods");
                                deliveryMsg.addReceiver(aid);
                                send(deliveryMsg);
                            }
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }
        }

        private void deleteGoodNeeds(Map<String, Integer> goods) {
            for (Map.Entry<String, Integer> good : goods.entrySet()) {
                final String name = good.getKey();
                final GoodNeed goodNeed = goodNeeds.get(name);
                if (goodNeed == null) {
                    logger.log(Level.SEVERE, String.format("Wrong good: %s. Buyer %s", name, getLocalName()));
                } else {
                    final boolean b = goodNeed.getQuantity() == good.getValue();
                    logger.log(Level.INFO, String.format("Buyer %s, quantity %s", getLocalName(), String.valueOf(b)));
                    goodNeeds.remove(name);
                }
            }
        }

        @Override
        public boolean done() {
            return step == 4;
        }
    }

    private class ProposalTable {
        private Map<String, CustomerProposal> proposalMap = new HashMap<>();

        public ProposalTable() {
        }

        public void addCustomerProposal(AID customer, String name, double cost, int rest) {
            CustomerProposal customerProposal = proposalMap.get(name);
            if (customerProposal == null) {
                proposalMap.put(name, new CustomerProposal(customer, cost, rest));
                return;
            }

            if (customerProposal.cost > cost) {
                proposalMap.put(name, new CustomerProposal(customer, cost, rest));
            }
            if ((customerProposal.cost == cost) && (customerProposal.rest > rest)) {
                proposalMap.put(name, new CustomerProposal(customer, cost, rest));
            }
        }

        public Set<Map.Entry<String, CustomerProposal>> getEntrySet() {
            return proposalMap.entrySet();
        }

        private class CustomerProposal {
            private AID customer;
            private double cost;
            private int rest;

            public CustomerProposal(AID customer, double cost, int rest) {
                this.customer = customer;
                this.cost = cost;
                this.rest = rest;
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