package study.masystems.purchasingsystem;

import flexjson.JSONSerializer;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionInitiator;
import study.masystems.purchasingsystem.defaultvalues.DefaultGoods;

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

    private List<String> goodNeeds;
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

        addBehaviour(new WaitforSupplier(this, waitForSupplier));
    }

    private void initialization() {
        //TODO: replace with GUI initialization.
        goodNeeds = DefaultGoods.getRandomGoods();
        goodNeedsJSON = jsonSerializer.serialize(goodNeeds);
        waitForSupplier = DefaultGoods.randLong(10000, 60000);
    }

    private class WaitforSupplier extends WakerBehaviour {
        public WaitforSupplier(Agent a, Date wakeupDate) {
            super(a, wakeupDate);
        }

        public WaitforSupplier(Agent a, long timeout) {
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
        private MessageTemplate mt; // The template to receive replies

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
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown();
        System.out.print(String.format("Customer %s terminate.", getAID().getName()));
    }
}
