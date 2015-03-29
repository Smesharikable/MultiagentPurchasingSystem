package study.masystems.purchasingsystem.agents;

import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionInitiator;
import study.masystems.purchasingsystem.GoodNeed;
import study.masystems.purchasingsystem.defaultvalues.DataGenerator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Initiator of procurement.
 */
public class Customer extends Agent {
    private JSONSerializer jsonSerializer = new JSONSerializer();

    private ACLMessage subscriptionMessage;
    private List<AID> suppliers = new ArrayList<AID>();

    private double money;
    private List<GoodNeed> goodNeeds;
    private String goodNeedsJSON;
    private long waitForSupplier;



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
            addBehaviour(new SendCFP());
        }
    }

    private class UnsubscribeBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            DFService.createCancelMessage(this.getAgent(), getDefaultDF(), subscriptionMessage);
        }
    }

    private class SendCFP extends OneShotBehaviour {
        private MessageTemplate proposal; // The template to receive replies

        @Override
        public void action() {
            // Send the cfp to all sellers
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID supplier : suppliers) {
                cfp.addReceiver(supplier);
            }
            cfp.setContent(goodNeedsJSON);
            cfp.setConversationId("book-trade");
            cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
            myAgent.send(cfp);
            // Prepare the template to get proposals
            proposal = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
        }
    }

    private class ReceiveSupplierProposals extends Behaviour{
        private MessageTemplate proposalTemplate;
        private int repliesCnt = 0;
        private boolean allReplies = false;

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
                    int price = Integer.parseInt(reply.getContent());

                    //TODO: Add needs check.
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
            return false;
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown();
        System.out.print(String.format("Customer %s terminate.", getAID().getName()));
    }
}
