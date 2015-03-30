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
import study.masystems.purchasingsystem.PurchaseInfo;
import study.masystems.purchasingsystem.PurchaseProposal;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.defaultvalues.DataGenerator;

import java.util.*;

/**
 * Initiator of procurement.
 */
public class Customer extends Agent {
    private JSONSerializer jsonSerializer = new JSONSerializer();
    private JSONDeserializer<Map<String, GoodNeed>> jsonDeserializer = new JSONDeserializer<Map<String, GoodNeed>>();

    FSMBehaviour startWholeSalePurchase;

    private ACLMessage subscriptionMessage;
    private MessageTemplate supplierProposalMT;
    private List<AID> suppliers = new ArrayList<AID>();

    private double money;
    private Map<String, GoodNeed> goodNeeds;
    private String goodNeedsJSON;
    private long waitForSupplier;

    private Purchase purchase = new Purchase();

    private static final int NEXT_STEP = 0;
    private static final int ABORT = 1;


    @Override
    protected void setup() {
        initialization();

        // Build the description used as template for the subscription
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription templateSd = new ServiceDescription();
        templateSd.setType("general-supplier");
        template.addServices(templateSd);

        subscriptionMessage = DFService.createSubscriptionMessage(this, getDefaultDF(), template, null);

        addBehaviour(new SubscriptionInitiator(this, subscriptionMessage) {
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

        addBehaviour(new WaitForSuppliers(this, waitForSupplier));

        startWholeSalePurchase = new FSMBehaviour() {
            @Override
            public int onEnd() {
                myAgent.doDelete();
                return super.onEnd();
            }
        };
        startWholeSalePurchase.registerFirstState(new SendCFP(), "SendCFP");
        startWholeSalePurchase.registerLastState(new FinalState(), "FinalState");
        startWholeSalePurchase.registerState(new ReceiveSupplierProposals(supplierProposalMT), "ReceiveSupplierProposal");
        startWholeSalePurchase.registerState(new RegisterPurchase(), "RegisterPurchase");
        startWholeSalePurchase.registerState(new HandleBuyerCFP(), "HandleBuyerCFP");

        startWholeSalePurchase.registerDefaultTransition("SendCFP", "ReceiverSupplierProposal");
        startWholeSalePurchase.registerTransition("ReceiverSupplierProposal", "RegisterPurchase", NEXT_STEP);
        startWholeSalePurchase.registerTransition("ReceiverSupplierProposal", "FinalState", ABORT);
        startWholeSalePurchase.registerDefaultTransition("RegisterPurchase", "HandleBuyerCFP");
        startWholeSalePurchase.registerDefaultTransition("HandleBuyerCFP", "FinalState");
    }

    private void initialization() {
        //TODO: replace with GUI initialization.
        money = DataGenerator.getRandomMoneyAmount();
        goodNeeds = DataGenerator.getRandomGoodNeeds();
        goodNeedsJSON = jsonSerializer.serialize(goodNeeds);
        waitForSupplier = DataGenerator.randLong(10000, 60000);
    }

    private class WaitForSuppliers extends WakerBehaviour {
        public WaitForSuppliers(Agent a, Date wakeupDate) {
            super(a, wakeupDate);
        }

        public WaitForSuppliers(Agent a, long timeout) {
            super(a, timeout);
        }

        @Override
        protected void onWake() {
            super.onWake();
            addBehaviour(new UnsubscribeBehaviour());
            if (suppliers.size() == 0) {
                doDelete();
                return;
            }
            //TODO: Add FSM
            addBehaviour(startWholeSalePurchase);
//            addBehaviour(new SendCFP());
        }
    }

    private class UnsubscribeBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFService.createCancelMessage(this.getAgent(), getDefaultDF(), subscriptionMessage);
        }
    }

    private class SendCFP extends OneShotBehaviour {
        @Override
        public void action() {
            // Send the cfp to all sellers
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID supplier : suppliers) {
                cfp.addReceiver(supplier);
            }
            cfp.setContent(goodNeedsJSON);
            cfp.setConversationId("wholesale-purchase");
            cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
            myAgent.send(cfp);
            // Prepare the template to get proposals
            supplierProposalMT = MessageTemplate.and(MessageTemplate.MatchConversationId("wholesale-purchase"),
                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

        }

//        @Override
//        public int onEnd() {
//            return NEXT_STEP;
//        }
    }

    private class ReceiveSupplierProposals extends Behaviour{
        private MessageTemplate proposalTemplate;
        private int repliesCnt = 0;
        private boolean allReplies = false;

        //TODO: Add time restriction.
        public ReceiveSupplierProposals(MessageTemplate proposalTemplate) {
            super();
            this.proposalTemplate = proposalTemplate;
        }

        @Override
        public void action() {
            // Receive all proposals/refusals from suppliers agents
            ACLMessage reply = myAgent.receive(proposalTemplate);
            if (reply != null) {
                // Reply received
                if (reply.getPerformative() == ACLMessage.PROPOSE) {
                    // This is an offer
                    HashMap<String, PurchaseProposal> goodsInfo =
                            new JSONDeserializer<HashMap<String, PurchaseProposal>>().deserialize(reply.getContent());
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

        @Override
        public boolean done() {
            return allReplies;
        }

        @Override
        public int onEnd(){
            System.out.println("End state Recieve");
            if (purchase.isFull()) {
                return NEXT_STEP;
            } else {
                return ABORT;
            }
        }
    }

    private class RegisterPurchase extends OneShotBehaviour {

        @Override
        public void action() {
            DFAgentDescription dfAgentDescription = new DFAgentDescription();
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setType("customer");
            dfAgentDescription.addServices(serviceDescription);

            try {
                DFService.register(myAgent, dfAgentDescription);
            } catch (FIPAException e) {
                e.printStackTrace();
            }
        }

//        @Override
//        public int onEnd() {
//            return NEXT_STEP;
//        }
    }

    private class HandleBuyerCFP extends Behaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                Map<String, GoodNeed> goodsRequest = new JSONDeserializer<Map<String, GoodNeed>>().deserialize(msg.getContent());
                ACLMessage reply = msg.createReply();
                HashMap<String, PurchaseProposal> requestedGoods = new HashMap<String, PurchaseProposal>();

                Map<String, Double> goodPrices = new HashMap<String, Double>();
                int deliveryPeriod = -1;
                for (Map.Entry<String, GoodNeed> good : goodsRequest.entrySet()){
                    String goodName = good.getKey();
                    PurchaseProposal purchaseProposal = purchase.purchaseTable.get(goodName);

                    deliveryPeriod = Math.max(deliveryPeriod, purchaseProposal.getDeliveryPeriodDays());
                    goodPrices.put(goodName, purchaseProposal.getCost());
                }

                if (goodPrices.size() > 0) {
                    PurchaseInfo purchaseInfo = new PurchaseInfo(deliveryPeriod, goodPrices);
                    // The requested goods are available for sale. Reply with the info
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(jsonSerializer.serialize(purchaseInfo));
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

        @Override
        public boolean done() {
            return false;
        }
    }

    private class FinalState extends OneShotBehaviour {

        @Override
        public void action() {
            takeDown();
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown();
        System.out.print(String.format("Customer %s terminate.", getAID().getName()));
    }

    private class Purchase {
        Map<String, PurchaseProposal> purchaseTable;

        public Purchase() {
        }

        public boolean isFull() {
            for (String name: goodNeeds.keySet()) {
                if (!purchaseTable.containsKey(name)) {
                    return false;
                }
            }
            return true;
        }

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
    }

}
