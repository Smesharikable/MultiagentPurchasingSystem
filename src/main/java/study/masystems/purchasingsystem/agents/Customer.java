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
import jade.proto.SubscriptionInitiator;
import jade.util.Logger;
import study.masystems.purchasingsystem.Demand;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.PurchaseProposal;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Initiator of procurement.
 */
public class Customer extends Agent {
    private int WAIT_FOR_SUPPLIERS_LIMIT = 3;
    private long WAIT_FOR_SUPPLIERS_TIMEOUT_MS = 5000;
    private long RECEIVE_SUPPLIERS_PROPOSAL_TIMEOUT_MS = 5000;
    private long RECEIVE_SUPPLIERS_AGREEMENT_TIMEOUT_MS = 2000;
    private int PURCHASE_NUMBER_LIMIT = 1;
    private long PURCHASE_TIMEOUT_MS = 10000;

    private JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");

    private JSONDeserializer<HashMap<String, PurchaseProposal>> supplierProposeDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Map<String, GoodNeed>> buyerProposeDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Demand> demandDeserializer = new JSONDeserializer<>();

    private double money;

    private Purchase purchase;

    private List<AID> suppliers = new ArrayList<>();
    private Map<AID, ACLMessage> suppliersProposal = new HashMap<>();
    private ACLMessage supplierSubscription;

    private static final int SUCCESS = 0;
    private static final int FAIL = 1;
    private static final int ABORT = 2;

    private static Logger logger = Logger.getMyLogger(Customer.class.getName());
    private MessageTemplate conversationWithSupplierMT;

    @Override
    protected void setup() {
        initialization();

        // Build the description used as template for the subscription
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription templateSd = new ServiceDescription();
        templateSd.setType("general-supplier");
        template.addServices(templateSd);

        supplierSubscription = DFService.createSubscriptionMessage(this, getDefaultDF(), template, null);

        addBehaviour(new SubscriptionInitiator(this, supplierSubscription) {
            protected void handleInform(ACLMessage inform) {
                System.out.println("Agent " + getLocalName() + ": Notification received from DF");
                try {
                    DFAgentDescription[] results = DFService.decodeNotification(inform.getContent());
                    if (results.length > 0) {
                        for (DFAgentDescription dfd : results) {
                            suppliers.add(dfd.getName());
                        }
                    }
                    System.out.println();
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });

        SequentialBehaviour customerBehaviour = new SequentialBehaviour() {
            @Override
            protected void scheduleNext(boolean currentDone, int currentResult) {
                super.scheduleNext(currentDone, currentResult);
                if (currentDone) {
                    switch (currentResult) {
                        case (FAIL): {
                            reset();
                            break;
                        }
                        case (ABORT): {
                            doDelete();
                            break;
                        }
                    }
                }
            }
        };
        customerBehaviour.addSubBehaviour(createFindSupplierBehaviour());
        customerBehaviour.addSubBehaviour(new PurchaseOrganization(this, PURCHASE_TIMEOUT_MS));

        addBehaviour(customerBehaviour);
    }

    private void initialization() {
        //TODO: replace with GUI initialization.
        //Check whether an agent was read from file or created manually
        //If read, then parse args.
        Object[] args = getArguments();
        Map<String, GoodNeed> goodNeeds;
        if (args == null || args.length == 0) {
            goodNeeds = DataGenerator.getRandomGoodNeeds();
            money = DataGenerator.getRandomMoneyAmount();
        } else {
            try {
                goodNeeds = (Map<String, GoodNeed>) args[0];
                money = (Integer) args[1];
            } catch (ClassCastException e) {
                logger.log(Logger.WARNING, "Class Cast Exception by Customer " + this.getAID().getName() + " creation");

                goodNeeds = DataGenerator.getRandomGoodNeeds();
                money = DataGenerator.getRandomMoneyAmount();
            }
        }
        purchase = new Purchase(getAID(), goodNeeds);
    }

    private void unsubscribeFromSuppliers() {
        DFService.createCancelMessage(this, getDefaultDF(), supplierSubscription);
    }

    /*
     * FindSupplier sequential behaviour classes.
     */

    private SequentialBehaviour createFindSupplierBehaviour() {
        SequentialBehaviour findSuppliers = new SequentialBehaviour(this) {
            private int counter = 0;
            private int status = SUCCESS;

            @Override
            public void onStart() {
                super.onStart();
                counter++;
                if (counter > WAIT_FOR_SUPPLIERS_LIMIT) {
                    skipNext();
                    status = ABORT;
                }
            }

            @Override
            public void reset() {
                super.reset();
                status = SUCCESS;
            }

            @Override
            protected void scheduleNext(boolean currentDone, int currentResult) {
                super.scheduleNext(currentDone, currentResult);
                if (currentDone) {
                    switch (currentResult) {
                        case (FAIL): {
                            if (counter == WAIT_FOR_SUPPLIERS_LIMIT) {
                                skipNext();
                                status = ABORT;
                            } else {
                                reset();
                            }
                            break;
                        }
                        case (ABORT): {
                            skipNext();
                            status = ABORT;
                            break;
                        }
                    }
                }
            }

            @Override
            public int onEnd() {
                return status;
            }
        };
        findSuppliers.addSubBehaviour(new WaitForSuppliers(this, WAIT_FOR_SUPPLIERS_TIMEOUT_MS));
        findSuppliers.addSubBehaviour(new GatheringProposal(this, RECEIVE_SUPPLIERS_PROPOSAL_TIMEOUT_MS));
        return findSuppliers;
    }

    private class WaitForSuppliers extends WakerBehaviour {
        private int status = SUCCESS;

        public WaitForSuppliers(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            if (suppliers.size() == 0) {
                status = FAIL;
            } else {
                logger.log(Level.INFO, String.format("Customer %s found supplier.", getLocalName()));
            }
        }

        @Override
        public void reset(long timeout) {
            super.reset(timeout);
            status = SUCCESS;
        }

        @Override
        public int onEnd() {
            return status;
        }
    }

    private class GatheringProposal extends Behaviour {
        private final int CFP_STATE = 0;
        private final int RECEIVE_PROPOSALS = 1;
        private int state = CFP_STATE;

        private final long timeout;
        private long endTime;

        private int repliesCnt = 0;
        private boolean allReplies = false;

        public GatheringProposal(Agent a, long timeout) {
            super(a);
            this.timeout = timeout;
        }

        @Override
        public void onStart() {
            super.onStart();
            endTime = System.currentTimeMillis() + timeout;
        }

        @Override
        public void action() {
            switch (state) {
                case CFP_STATE: {
                    // Send the cfp to all sellers
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    suppliers.forEach(cfp::addReceiver);
                    cfp.setContent(purchase.getGoodNeedsJSON());
                    String convId = "wholesale-purchase" + hashCode() + "_" + System.currentTimeMillis();
                    cfp.setConversationId(convId);
                    cfp.setReplyWith("cfp" + "_" + System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    conversationWithSupplierMT = MessageTemplate.and(MessageTemplate.MatchConversationId(convId),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    state = RECEIVE_PROPOSALS;
                    logger.log(Level.INFO, String.format("Customer %s send CFP.", getLocalName()));
                    break;
                }
                case RECEIVE_PROPOSALS: {
                    // Receive all proposals/refusals from suppliers agents
                    ACLMessage reply = myAgent.receive(conversationWithSupplierMT);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            suppliersProposal.put(reply.getSender(), reply);
                            // This is an offer
                            HashMap<String, PurchaseProposal> goodsInfo =
                                    supplierProposeDeserializer.use("values", PurchaseProposal.class).deserialize(reply.getContent());
                            for (Map.Entry<String, PurchaseProposal> entry : goodsInfo.entrySet()) {
                                purchase.addProposal(entry.getKey(), entry.getValue());
                            }
                        }
                        repliesCnt++;
                        allReplies = (repliesCnt >= suppliers.size());
                        logger.log(Level.INFO, String.format("Customer receive proposal.", getLocalName()));
                    } else {
                        block();
                    }
                    break;
                }
            }
        }

        @Override
        public boolean done() {
            return allReplies || endTime <= System.currentTimeMillis();
        }

        @Override
        public int onEnd() {
            if (!purchase.isFull()) {
                // Reset FindSupplier behaviour.
                return FAIL;
            }
            return SUCCESS;
        }
    }

    /*
     * Purchase sequential behaviour classes.
     */

    private class PurchaseOrganization extends SequentialBehaviour {
        private int purchaseCounter = 0;
        private int status = SUCCESS;

        public PurchaseOrganization(Agent a, long purchasePeriod) {
            super(a);
            addSubBehaviour(new OpenPurchase(myAgent));
            addSubBehaviour(new ClosePurchase(myAgent, purchasePeriod));
            addSubBehaviour(new PlaceOrderBehaviour(RECEIVE_SUPPLIERS_AGREEMENT_TIMEOUT_MS));
            addSubBehaviour(new SendConfirmation());
            myAgent.addBehaviour(createCommunicationWithBuyersBehaviour());
        }

        @Override
        public void onStart() {
            super.onStart();
            purchaseCounter++;
            if (purchaseCounter > PURCHASE_NUMBER_LIMIT) {
                skipNext();
                status = ABORT;
            }
        }

        @Override
        protected void scheduleNext(boolean currentDone, int currentResult) {
            super.scheduleNext(currentDone, currentResult);
            if (currentDone) {
                switch (currentResult) {
                    case (FAIL): {
                        cancelPurchase();
                        if (purchaseCounter >= PURCHASE_NUMBER_LIMIT) {
                            skipNext();
                            status = ABORT;
                        } else {
                            reset();
                        }
                        break;
                    }
                    case (ABORT): {
                        cancelPurchase();
                        skipNext();
                        if (purchaseCounter >= PURCHASE_NUMBER_LIMIT) {
                            status = ABORT;
                            logger.log(
                                    Level.INFO,
                                    String.format("Customer %s: purchase organization has been aborted.", getLocalName())
                            );
                        } else {
                            status = FAIL;
                        }
                    }
                }
            }
        }

        @Override
        public void reset() {
            super.reset();
            status = SUCCESS;
        }

        private void cancelPurchase() {
            sendCancelMessageToBuyers();
            purchase.clear();
        }

        private void sendCancelMessageToBuyers() {
            Set<AID> buyers = purchase.getBuyers();
            ACLMessage cancelMessage = new ACLMessage(ACLMessage.CANCEL);
            cancelMessage.setConversationId(purchase.getPurchaseConvId());
            buyers.forEach(cancelMessage::addReceiver);
            myAgent.send(cancelMessage);
        }

        @Override
        public int onEnd() {
            return status;
        }
    }

    private class OpenPurchase extends OneShotBehaviour {
        public OpenPurchase(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            purchase.addOwnDemand();
            try {
                purchase.register(myAgent);
                purchase.open();
            } catch (FIPAException e) {
                logger.log(Level.SEVERE, "Error while register purchase, agent: " + myAgent.getLocalName());
            }
        }
    }

    private class ClosePurchase extends WakerBehaviour {
        private int status = SUCCESS;

        public ClosePurchase(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        public void reset(long timeout) {
            super.reset(timeout);
            status = SUCCESS;
        }

        @Override
        protected void onWake() {
            super.onWake();
            purchase.close();
            try {
                purchase.deregister(myAgent);
            } catch (FIPAException e) {
                e.printStackTrace();
            }
            if (!purchase.isFormed()) {
                status = FAIL;
                logger.log(Level.INFO, String.format("%s reset purchase!", getLocalName()));
            } else {
                logger.log(Level.INFO, String.format("%s purchase completed!", getLocalName()));
            }
        }

        @Override
        public int onEnd() {
            return status;
        }
    }

    private class PlaceOrderBehaviour extends Behaviour {
        private final long timeout;

        private static final int SEND_STATE = 0;
        private static final int RECEIVE_STATE = 1;
        private int state = SEND_STATE;

        private long endTime;
        private int receivedCount = 0;
        private MessageTemplate mt;

        private Integer status = 0;

        private  Map<AID, Set<String>> suppliersTable;

        public PlaceOrderBehaviour(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void onStart() {
            super.onStart();
            endTime = System.currentTimeMillis() + timeout;
            suppliersTable = purchase.getSuppliersTable();
        }

        @Override
        public void action() {
            switch (state) {
                case SEND_STATE: {
                    mt = send();
                    state = RECEIVE_STATE;
                    break;
                }
                case RECEIVE_STATE: {
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        handleReply(reply);
                        receivedCount ++;
                    } else {
                        block();
                    }
                    break;
                }
            }
        }

        protected MessageTemplate send() {
            logger.log(Level.INFO, String.format("Customer %s send order to suppliers", myAgent.getLocalName()));
            final String conversationId = "purchase order" + hashCode() + System.currentTimeMillis();
            suppliersTable.forEach((supplier, goods) -> {
                // Form order for supplier.
                Map<String, Integer> order = new HashMap<>();
                goods.forEach(good -> order.put(good, purchase.getTotalDemand(good)));

                // Send order to supplier.
                ACLMessage message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                message.addReceiver(supplier);
                message.setConversationId(conversationId);
                message.setContent(jsonSerializer.serialize(order));
                myAgent.send(message);

            });
            return MessageTemplate.MatchConversationId(conversationId);
        }

        protected void handleReply(ACLMessage reply) {
            switch (reply.getPerformative()) {
                case ACLMessage.CONFIRM: {
                    // Ok.
                    break;
                }
                case ACLMessage.REFUSE: {
                    status = ABORT;
                    break;
                }
                default: {
                    status = ABORT;
                    break;
                }
            }
        }

        @Override
        public boolean done() {
            return allReceived() || (endTime <= System.currentTimeMillis());
        }

        @Override
        public void reset() {
            super.reset();
            state = SEND_STATE;
            receivedCount = 0;
            status = SUCCESS;
        }

        @Override
        public int onEnd() {
            if (!allReceived()) {
                return ABORT;
            }
            return status;
        }

        private boolean allReceived() {
            return receivedCount >= suppliersTable.size();
        }
    }

    private class SendConfirmation extends OneShotBehaviour {

        @Override
        public void action() {
            Set<AID> buyers = purchase.getBuyers();
            ACLMessage confirmation = new ACLMessage(ACLMessage.CONFIRM);
            buyers.forEach(confirmation::addReceiver);
            confirmation.setConversationId(purchase.getPurchaseConvId());
            confirmation.setContent(jsonSerializer.serialize(buyers));
            myAgent.send(confirmation);
        }
    }

    /*
     * Communication with buyers classes.
     */

    private ParallelBehaviour createCommunicationWithBuyersBehaviour() {
        ParallelBehaviour parallelBehaviour = new ParallelBehaviour();
        parallelBehaviour.addSubBehaviour(new HandleBuyerCFP());
        parallelBehaviour.addSubBehaviour(new AddBuyerToParty());
        return parallelBehaviour;
    }

    /**
     * Receive CFP from buyers and send reply with proposal.
     */
    private class HandleBuyerCFP extends Behaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);

            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Map<String, GoodNeed> goodsRequest = buyerProposeDeserializer.use("values", GoodNeed.class).deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();

                Map<String, Double> goodPrices = new HashMap<>();
                int deliveryPeriod = -1;
                for (Map.Entry<String, GoodNeed> good : goodsRequest.entrySet()) {
                    String goodName = good.getKey();
                    PurchaseProposal purchaseProposal = purchase.purchaseTable.get(goodName);
                    if (purchaseProposal != null) {
                        deliveryPeriod = Math.max(deliveryPeriod, purchaseProposal.getDeliveryPeriodDays());
                        goodPrices.put(goodName, purchaseProposal.getCost());
                    }
                }

                if (goodPrices.size() > 0) {
                    PurchaseInfo purchaseInfo = new PurchaseInfo(deliveryPeriod, goodPrices);
                    // The requested goods are available for sale. Reply with proposal.
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(jsonSerializer.exclude("*.class").serialize(purchaseInfo));
                }
                else {
                    // We don't have requested goods.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
                logger.log(Level.INFO, String.format("Customer %s replied to buyer.", getLocalName()));
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    /**
     * Add buyer to the party if purchase is open.
     */
    private class AddBuyerToParty extends Behaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                final String conversationId = msg.getConversationId();
                if (!conversationId.equals(purchase.getPurchaseConvId())|| !purchase.isOpen()) {
                    reply.setPerformative(ACLMessage.REFUSE);
                } else {
                    Demand demand = demandDeserializer.use("orders",HashMap.class).deserialize(msg.getContent(), Demand.class);
                    boolean success = purchase.addDemand(msg.getSender(), demand);
                    if (success) {
                        reply.setPerformative(ACLMessage.AGREE);
                    } else {
                        reply.setPerformative(ACLMessage.UNKNOWN);
                    }
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown();
        logger.log(Level.INFO, String.format("Customer %s terminate.", getAID().getName()));
    }

    /**
     * Current purchase state. Maintain table of goods with proposals.
     */
    private static class Purchase {
        private AID customer;
        private Map<String, GoodNeed> goodNeeds;
        private String goodNeedsJSON;

        private Map<String, PurchaseProposal> purchaseTable = new HashMap<>();
        private Map<String, DemandTable> demandTable = new HashMap<>();

        private PurchaseState purchaseState = PurchaseState.NONE;
        private String purchaseConvId = "";
        private DFAgentDescription purchaseDescription;

        public Purchase(AID customer, Map<String, GoodNeed> goodNeeds) {
            this.customer = customer;
            this.goodNeeds = goodNeeds;
            this.goodNeedsJSON = (new JSONSerializer().exclude("*.class")).serialize(goodNeeds);
        }

        /**
         * Add new proposal to the table. Replace old one if new is better.
         *
         * @param name        good's name.
         * @param newProposal new proposal.
         */
        public void addProposal(String name, PurchaseProposal newProposal) {
            GoodNeed goodNeed = goodNeeds.get(name);

            int deliveryPeriod = goodNeed.getDeliveryPeriodDays();
            if (deliveryPeriod < newProposal.getDeliveryPeriodDays()) {
                return;
            }

            PurchaseProposal oldProposal = purchaseTable.get(name);
            if (oldProposal == null) {
                purchaseTable.put(name, newProposal);
            } else {
                if (compareProposal(oldProposal, newProposal) < 0) {
                    purchaseTable.put(name, newProposal);
                }
            }
        }

        public int compareProposal(PurchaseProposal left, PurchaseProposal right) {
            if (left.getCost() < right.getCost()) {
                return 1;
            }
            if (left.getCost() > right.getCost()) {
                return -1;
            }
            return 0;
        }

        public void addOwnDemand() {
            Demand demand = new Demand();
            goodNeeds.forEach((good, goodNeed) -> {
                demand.put(good, goodNeed.getQuantity());
            });
            addDemand(customer, demand);
        }

        public boolean addDemand(AID buyer, Demand demand) {
            Map<String, Integer> orders = demand.getOrders();
            for (Map.Entry<String, Integer> entry: orders.entrySet()) {
                boolean success = addDemand(buyer, entry.getKey(), entry.getValue());
                if (!success) return false;
            }
            return true;
        }

        /**
         * @param buyer The buyer.
         * @param good  The good demanded.
         * @param count The goods count.
         * @return <tt>true<tt/>, if demand added successfully; false, if good is not found.
         */
        public boolean addDemand(AID buyer, String good, int count) {
            if (!purchaseTable.containsKey(good)) {
                return false;
            }
            DemandTable demand = demandTable.get(good);
            if (demand == null) {
                demandTable.put(good, new DemandTable(buyer, count));
            } else {
                demand.put(buyer, count);
            }
            return true;
        }

        public void register(Agent customer) throws FIPAException {
            DFService.register(customer, getNewPurchaseDescription());
        }

        public void deregister(Agent customer) throws FIPAException {
            DFService.deregister(customer, getPurchaseDescription());
        }

        public void clear() {
            demandTable.clear();
        }

        public String getGoodNeedsJSON() {
            return goodNeedsJSON;
        }

        public String getPurchaseConvId() {
            return purchaseConvId;
        }

        private String getNewPurchaseConvId() {
            purchaseConvId = "purchase" + hashCode() + "_" + System.currentTimeMillis();
            return purchaseConvId;
        }

        private DFAgentDescription getPurchaseDescription() {
            return purchaseDescription;
        }

        private DFAgentDescription getNewPurchaseDescription() {
            this.purchaseDescription = new DFAgentDescription();
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setType("customer");
            serviceDescription.setName(getNewPurchaseConvId());
            this.purchaseDescription.addServices(serviceDescription);
            return  purchaseDescription;
        }

        public Set<AID> getBuyers() {
            Set<AID> buyers = new HashSet<>();
            demandTable.forEach((good, demand) -> buyers.addAll(demand.getBuyers()));
            return buyers;
        }

        public Set<AID> getSuppliers() {
            Set<AID> suppliers = new HashSet<>();
            purchaseTable.forEach((good, purchase) -> suppliers.add(purchase.getSupplier()));
            return suppliers;
        }

        public Map<AID, Set<String>> getSuppliersTable() {
            Map<AID, Set<String>> suppliersTable = new HashMap<>();
            purchaseTable.forEach((good, purchase) -> {
                AID supplier = purchase.getSupplier();
                Set<String> goods = suppliersTable.get(supplier);
                if (goods == null) {
                    goods = new HashSet<>();
                    goods.add(good);
                    suppliersTable.put(supplier, goods);
                } else {
                    goods.add(good);
                }
            });
            return suppliersTable;
        }

        public int getTotalDemand(String good) {
            DemandTable demand = demandTable.get(good);
            if (demand == null) {
                throw new NoSuchElementException("Good " + good + " not found.");
            }
            return demand.getTotal();
        }

        /**
         * Check whether all good needs are satisfied by suppliers.
         *
         * @return <tt>true</tt>, if all good needs are satisfied.
         */
        public boolean isFull() {
            for (String name : goodNeeds.keySet()) {
                if (!purchaseTable.containsKey(name)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Check whether all requirements for purchase are satisfied.
         *
         * @return <tt>true</tt>, if all requirements are satisfied.
         */
        public boolean isFormed() {
            for (Map.Entry<String, PurchaseProposal> entry : purchaseTable.entrySet()) {
                int minimalQuantity = entry.getValue().getMinimalQuantity();
                DemandTable demand = demandTable.get(entry.getKey());
                if (demand == null) {
                    return false;
                }
                int totalDemand = demand.getTotal();
                if (minimalQuantity > totalDemand) {
                    return false;
                }
            }
            return true;
        }

        private enum PurchaseState {
            NONE, OPEN, CLOSED
        }

        public boolean isOpen() {
            return purchaseState == PurchaseState.OPEN;
        }

        public void open() {
            purchaseState = PurchaseState.OPEN;
        }

        public void close() {
            purchaseState = PurchaseState.CLOSED;
        }

        private class DemandTable {
            Map<AID, Integer> demand = new HashMap<>();

            public DemandTable() {
            }

            public DemandTable(AID buyer, int count) {
                demand.put(buyer, count);
            }

            public void put(AID buyer, int count) {
                demand.put(buyer, count);
            }

            public int getTotal() {
                final int[] totalDemand = {0};
                demand.forEach(new BiConsumer<AID, Integer>() {
                    @Override
                    public void accept(AID buyer, Integer count) {
                        totalDemand[0] += count;
                    }
                });
                return totalDemand[0];
            }

            public Set<AID> getBuyers() {
                return demand.keySet();
            }
        }
    }
}
