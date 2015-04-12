package study.masystems.purchasingsystem.agents;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionInitiator;
import jade.util.Logger;
import javafx.util.Pair;
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.PurchaseProposal;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.utils.DataGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Initiator of procurement.
 */
public class Customer extends Agent {
    private int WAIT_FOR_SUPPLIERS_LIMIT = 3;
    private long WAIT_FOR_SUPPLIERS_TIMEOUT_MS = 5000;
    private long RECEIVE_SUPPLIER_PROPOSAL_TIMEOUT_MS = 5000;
    private int PURCHASE_NUMBER_LIMIT = 1;
    private long PURCHASE_TIMEOUT_MS = 10000;
    private static Logger logger = Logger.getMyLogger("Customer");

    private PurchaseState purchaseState = PurchaseState.NONE;

    private JSONSerializer jsonSerializer = new JSONSerializer();
    private JSONDeserializer<HashMap<String, PurchaseProposal>> supplierProposeDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Map<String, GoodNeed>> buyerProposeDeserializer = new JSONDeserializer<>();
    private JSONDeserializer<Pair<String, Integer>> demandDeserializer = new JSONDeserializer<>();

    private List<AID> suppliers = new ArrayList<>();
    private ACLMessage supplierSubscription;

    private double money;
    private Map<String, GoodNeed> goodNeeds;
    private String goodNeedsJSON;

    private Purchase purchase = new Purchase();

    private static final int NEXT_STEP = 0;
    private static final int ABORT = 1;

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    public Map<String, GoodNeed> getGoodNeeds() {
        return goodNeeds;
    }

    public void setGoodNeeds(Map<String, GoodNeed> goodNeeds) {
        this.goodNeeds = goodNeeds;
    }

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

        SequentialBehaviour customerBehaviour = new SequentialBehaviour();
        customerBehaviour.addSubBehaviour(createFindSupplierBehaviour());
        customerBehaviour.addSubBehaviour(new PurchaseOrganization(this, PURCHASE_TIMEOUT_MS));

        addBehaviour(customerBehaviour);
    }

    private void initialization() {
        //TODO: replace with GUI initialization.
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
                logger.log(Logger.WARNING, "Class Cast Exception by Customer " + this.getAID().getName() + " creation");
                System.err.println("Class Cast Exception by Customer " + this.getAID().getName() + " creation");

                goodNeeds = DataGenerator.getRandomGoodNeeds();
                money = DataGenerator.getRandomMoneyAmount();
            }
        }
        goodNeedsJSON = jsonSerializer.serialize(goodNeeds);
        WAIT_FOR_SUPPLIERS_TIMEOUT_MS = DataGenerator.randLong(10000, 60000);
    }

    private void unsubscribeFromSuppliers() {
        DFService.createCancelMessage(this, getDefaultDF(), supplierSubscription);
    }

    /*
     * FindSupplier sequential behaviour classes.
     */

    private SequentialBehaviour createFindSupplierBehaviour() {
        SequentialBehaviour findSuppliers = new SequentialBehaviour(this);
        findSuppliers.addSubBehaviour(new WaitForSuppliers(this, WAIT_FOR_SUPPLIERS_TIMEOUT_MS));
        findSuppliers.addSubBehaviour(new GatheringProposal(this, RECEIVE_SUPPLIER_PROPOSAL_TIMEOUT_MS));
        return findSuppliers;
    }

    private enum PurchaseState {
        NONE, OPEN, CLOSED
    }

    private class WaitForSuppliers extends WakerBehaviour {
        private int counter = 0;

        public WaitForSuppliers(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            counter ++;
            if (suppliers.size() == 0) {
                if (counter > WAIT_FOR_SUPPLIERS_LIMIT) {
                    doDelete();
                } else {
                    parent.reset();
                }
            }
        }
    }

    private class GatheringProposal extends Behaviour {
        private final int CFP_STATE = 0;
        private final int RECEIVE_PROPOSALS = 1;
        private int state = CFP_STATE;

        private final long endTime;

        private MessageTemplate supplierProposalMT;
        private int repliesCnt = 0;
        private boolean allReplies = false;

        public GatheringProposal(Agent a, long timeout) {
            super(a);
            this.endTime = timeout + System.currentTimeMillis();
        }

        @Override
        public void action() {
            switch (state) {
                case CFP_STATE: {
                    // Send the cfp to all sellers
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    suppliers.forEach(cfp::addReceiver);
                    cfp.setContent(goodNeedsJSON);
                    cfp.setConversationId("customer");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    supplierProposalMT = MessageTemplate.and(MessageTemplate.MatchConversationId("wholesale-purchase"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    state = RECEIVE_PROPOSALS;
                    break;
                }
                case RECEIVE_PROPOSALS: {
                    // Receive all proposals/refusals from suppliers agents
                    ACLMessage reply = myAgent.receive(supplierProposalMT);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // This is an offer
                            HashMap<String, PurchaseProposal> goodsInfo =
                                    supplierProposeDeserializer.deserialize(reply.getContent());
                            //TODO: Add needs check.
                            for (Map.Entry<String, PurchaseProposal> entry: goodsInfo.entrySet()) {
                                purchase.addProposal(entry.getKey(), entry.getValue());
                            }
                        }
                        repliesCnt++;
                        allReplies = (repliesCnt >= suppliers.size());
                    }
                    else {
                        block();
                    }
                }
            }
        }

        @Override
        public boolean done() {
            return allReplies || endTime <= System.currentTimeMillis();
        }

        @Override
        public int onEnd(){
            if (!purchase.isFull()) {
                // Reset FindSupplier behaviour.
                parent.reset();
            }
            return NEXT_STEP;
        }
    }

    /*
     * Purchase sequential behaviour classes.
     */

    private class PurchaseOrganization extends SequentialBehaviour {
        private int purchaseCounter = 1;

        public PurchaseOrganization(Agent a, long purchasePeriod) {
            super(a);
            addSubBehaviour(new OpenPurchase(myAgent));
            addSubBehaviour(new ClosePurchase(myAgent, purchasePeriod));
            //TODO: add PlaceOrder and ConfirmPurchase.
            myAgent.addBehaviour(createCommunicationWithBuyersBehaviour());
        }

        @Override
        public void reset() {
            super.reset();
            cancelPurchase();
            if (purchaseCounter > PURCHASE_NUMBER_LIMIT) {
                doDelete();
            } else {
                // We will try to organize one more purchase.
                purchaseCounter ++;
            }
        }

        private void cancelPurchase() {
            sendCancelMessageToBuyers();
            purchase.clear();
        }

        private void sendCancelMessageToBuyers() {
            Set<AID> buyers = purchase.getBuyers();
            ACLMessage cancelMessage = new ACLMessage(ACLMessage.CANCEL);
            //TODO: use ontology?
            cancelMessage.setConversationId("participation");
            buyers.forEach(cancelMessage::addReceiver);
            myAgent.send(cancelMessage);
        }
    }

    private class OpenPurchase extends OneShotBehaviour {
        public OpenPurchase(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            DFAgentDescription dfAgentDescription = new DFAgentDescription();
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setType("customer");
            dfAgentDescription.addServices(serviceDescription);

            try {
                DFService.register(myAgent, dfAgentDescription);
                purchaseState = PurchaseState.OPEN;
            } catch (FIPAException e) {
                // TODO: Log error.
                e.printStackTrace();
            }
        }
    }

    private class ClosePurchase extends WakerBehaviour {

        public ClosePurchase(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            purchaseState = PurchaseState.CLOSED;
            if (!purchase.isFormed()) {
                parent.reset();
                System.out.println("Purchase reset!");
            } else {
                System.out.println("Purchase completed");
            }
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
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId("participation")
            );
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Map<String, GoodNeed> goodsRequest = buyerProposeDeserializer.deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();

                Map<String, Double> goodPrices = new HashMap<>();
                int deliveryPeriod = -1;
                for (Map.Entry<String, GoodNeed> good : goodsRequest.entrySet()){
                    String goodName = good.getKey();
                    PurchaseProposal purchaseProposal = purchase.purchaseTable.get(goodName);

                    deliveryPeriod = Math.max(deliveryPeriod, purchaseProposal.getDeliveryPeriodDays());
                    goodPrices.put(goodName, purchaseProposal.getCost());
                }

                if (goodPrices.size() > 0) {
                    PurchaseInfo purchaseInfo = new PurchaseInfo(deliveryPeriod, goodPrices);
                    // The requested goods are available for sale. Reply with proposal.
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(jsonSerializer.serialize(purchaseInfo));
                }
                else {
                    // We have not requested goods.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
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
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchConversationId("participation")
            );
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                //TODO: add timestamp check.
                Pair<String, Integer> demand = demandDeserializer.deserialize(msg.getContent());
                boolean success = purchase.addDemand(msg.getSender(), demand.getKey(), demand.getValue());
                ACLMessage reply = msg.createReply();
                if (success) {
                    reply.setPerformative(ACLMessage.AGREE);
                } else {
                    reply.setPerformative(ACLMessage.UNKNOWN);
                    myAgent.send(reply);
                }
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
        unsubscribeFromSuppliers();
        System.out.print(String.format("Customer %s terminate.", getAID().getName()));
    }

    /**
     * Current purchase state. Maintain table of goods with proposals.
     */
    private class Purchase {
        Map<String, PurchaseProposal> purchaseTable = new HashMap<>();
        Map<String, DemandTable> demandTable = new HashMap<>();

        public Purchase() {
        }

        /**
         * Add new proposal to the table. Replace old one if new is better.
         * @param name good's name.
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

        /**
         *
         * @param buyer -- the buyer.
         * @param good -- good demanded.
         * @param count -- goods count.
         * @return true, if demand added successfully; false, if good if not found.
         */
        public boolean addDemand(AID buyer, String good, int count) {
            DemandTable demand = demandTable.get(good);
            if (demand == null) {
                return false;
            } else {
                demand.put(buyer, count);
                return true;
            }
        }

        public void clear() {
            demandTable.clear();
        }

        public Set<AID> getBuyers() {
            Set<AID> buyers = new HashSet<>();
            demandTable.forEach((good, demand) -> {
                buyers.addAll(demand.getBuyers());
            });
            return buyers;
        }

        public boolean isFull() {
            for (String name: goodNeeds.keySet()) {
                if (!purchaseTable.containsKey(name)) {
                    return false;
                }
            }
            return true;
        }

        public boolean isFormed() {
            for (Map.Entry<String, PurchaseProposal> entry: purchaseTable.entrySet()) {
                int minimalQuantity = entry.getValue().getMinimalQuantity();
                int totalDemand = demandTable.get(entry.getKey()).getTotal();
                if (minimalQuantity > totalDemand) {
                    return false;
                }
            }
            return true;
        }

        private class DemandTable {
            Map<AID, Integer> demand = new HashMap<>();

            public void put(AID buyer, int count) {
                demand.put(buyer, count);
            }

            public int getTotal() {
                final int[] totalDemand = {0};
                demand.forEach((buyer, count) -> {
                    totalDemand[0] += count;
                });
                return totalDemand[0];
            }

            public Set<AID> getBuyers() {
                return demand.keySet();
            }
        }
    }

}
