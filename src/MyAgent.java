package src;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class MyAgent extends Agent {

	//Global Variables (IDs, graph, lists, metrics)
	private int myId;
	private int totalAgents;
	private AID nextAgentAID;
	private Graph<Integer, DefaultWeightedEdge> graph;
	private int currentLocation;
	private List<Integer> unvisitedNodes; // Available nodes for reaching
	private double totalDist = 0;
	//Using the Inform ACL Message we inform every agent about the removed node or the current location
	private void broadcastVisit(int node) {
		ACLMessage info = new ACLMessage(ACLMessage.INFORM);
		for (int i = 0; i < totalAgents; i++) {
			if (i != myId)
				info.addReceiver(new AID("Salesman-" + i, AID.ISLOCALNAME));
		}
		info.setContent(String.valueOf(node));
		info.setConversationId("node-visited");
		send(info);
	}
	@Override
	protected void setup() {
		//Recieve the arguments from the current agent
        Object[] args = getArguments();
        
        //Initialize variables with default values
        long seed = 0;
        int numNodes = 5;
        this.totalAgents = 1;
        this.currentLocation = 0;

        //We parse the arguments passed to the agent and assign them to the agent's instance variables
        if (args != null && args.length >= 4) {
            seed = Long.parseLong((String) args[0]);
            numNodes = Integer.parseInt((String) args[1]);
            this.totalAgents = Integer.parseInt((String) args[2]);
            this.currentLocation = Integer.parseInt((String) args[3]);
        }

        //Generation of the Random Weighted Graph
        this.graph = generateRandomGraph(numNodes, seed);
        
        //Initialization of the unvisitedNodes list 
        this.unvisitedNodes = new ArrayList<>(this.graph.vertexSet());
		//Starting point considered already visited
        if (unvisitedNodes.contains(currentLocation)) {
            unvisitedNodes.remove(Integer.valueOf(currentLocation));
        }

        //Retrieve of current Agent's name & id
        String localName = getAID().getLocalName();
        this.myId = Integer.parseInt(localName.substring(localName.lastIndexOf("-") + 1));
        
        //For the purpose od the ring topology (token circulation) we retrieve the next Agent's AID
        int nextId = (this.myId + 1) % totalAgents;
        this.nextAgentAID = new AID("Salesman-" + nextId, AID.ISLOCALNAME);

        //Register & Print Info
        registerService();
        System.out.println("Agent " + localName + " initialized at Node " + currentLocation + ". Map size: " + numNodes);

        //Behaviors
        addBehaviour(new TokenRingListenerBehaviour());
        addBehaviour(new MoveCommandListenerBehaviour());
		addBehaviour(new VisitedListener());
		addBehaviour(new StatsResponderBehaviour());
		//Broadcast the nodes that have been used for starting points + delay
		addBehaviour(new WakerBehaviour(this, 1000) {
			protected void onWake() {
				if (unvisitedNodes.contains(currentLocation)) {
					unvisitedNodes.remove(Integer.valueOf(currentLocation));
				}
				broadcastVisit(currentLocation);
			}
		});
        //Agent 0 starts the process after a brief delay
        if (this.myId == 0) {
            addBehaviour(new WakerBehaviour(this, 2000) {
                protected void onWake() {
                    System.out.println("--- SIMULATION START ---");
					//Start the new round
                    startNewRound();
                }
            });
        }
    }

	//Random Graph Generation Function G=(V-int,WE:DefaultWeightedEdge) 
    private Graph<Integer, DefaultWeightedEdge> generateRandomGraph(int numNodes, long seed) {
        Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        //For the random edge weights
		Random r = new Random(seed);

        //Creation of Vertices
        for (int i = 0; i < numNodes; i++) 
			g.addVertex(i);

        //Creation of the Complete Graph with Random Weights for connectivity
        //Comparison of current and next node
		for (int i = 0; i < numNodes; i++) {
            for (int j = i + 1; j < numNodes; j++) {
                DefaultWeightedEdge e = g.addEdge(i, j);
                //Random weight between 10 and 100
                double weight = 10 + r.nextInt(90); 
                g.setEdgeWeight(e, weight);
            }
        }
        return g;
    }
	//Registration of the MTSP-Agent service
	private void registerService() {
        DFAgentDescription dfad = new DFAgentDescription();
        dfad.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("travelling-salesman");
        sd.setName("mtsp-agent");
        dfad.addServices(sd);
        try {
			 DFService.register(this, dfad); 
			} 
			catch (FIPAException ex) {
				 ex.printStackTrace(); 
			}
    }

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
		}
	}

	//Helper Methods
	/*The startNewRound() function is called by the "leader" agent who 
	announces the minimum distance and starts a new round with a token 
	containing his weights
	*/
	public void startNewRound() {
		if (unvisitedNodes.isEmpty()) {
			System.out.println("FINISHED: All nodes visited!");
			System.out.println(getLocalName() + " Personal Total: " + totalDist);
			//To keep the total distance for every agent we created the behaviour class StatsCollectorBehaviour
			addBehaviour(new StatsCollectorBehaviour());
			return;
		}

		System.out.println(getLocalName() + " starting new round. Unvisited: " + unvisitedNodes);

		MovementProposal proposal = new MovementProposal();
		proposal.roundLeaderId = this.myId;

		//Initialize the vector with the current agent distances
		calculateAndFillProposals(proposal);

		//Send the token to next agent
		sendToken(proposal);
	}

	/*The function calculateAndFillProposals() calculates distances from 
	current location to all unvisited nodes
	and updates the proposal .*/
	private void calculateAndFillProposals(MovementProposal proposal) {
		//To calculate the minimum distance we use the Dijkstra Algorithm
		DijkstraShortestPath<Integer, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);

		for (Integer target : unvisitedNodes) {
			//Calculating distance
			double dist = Double.MAX_VALUE;
			try {
				//The path between the current node and the unvisited target
				var path = dijkstra.getPath(currentLocation, target);
				if (path != null)
				//Recieves weight of the path
					dist = path.getWeight();
				//Source=Target
				else if (currentLocation == target)
					dist = 0;
			} catch (IllegalArgumentException e) {
				// Target unreachable
			}

			//Comparisson of the proposals and check for the best proposal so far
			MovementProposal.Offer currentBest = proposal.bestOffers.get(target);

			if (currentBest == null || dist < currentBest.distance) {
				//Best Proposal
				proposal.bestOffers.put(target, new MovementProposal.Offer(this.myId, dist));
			}
		}
	}
	// Sends the Proposal/Token
	private void sendToken(MovementProposal proposal) {
		ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
		//Send the token to the next Agent
		msg.addReceiver(nextAgentAID);
		try {
			//Fill the message with the proposal's content & send it
			msg.setContentObject(proposal);
			send(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* For debugging purposes
	//Hardcoded graph for testing 
	private Graph<Integer, DefaultWeightedEdge> createGraph() {
		Graph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		//We add vertices from 0 to 5
		for (int i = 0; i <= 5; i++)
			g.addVertex(i);

		//We add edges with random picked weights
		addEdge(g, 0, 1, 10);
		addEdge(g, 1, 2, 10);
		addEdge(g, 2, 3, 10);
		addEdge(g, 3, 4, 10);
		addEdge(g, 4, 0, 50);
		addEdge(g, 0, 5, 100);
		return g;
	}

	private void addEdge(Graph<Integer, DefaultWeightedEdge> g, int v1, int v2, double weight) {
		DefaultWeightedEdge e = g.addEdge(v1, v2);
		g.setEdgeWeight(e, weight);
	}
	*/

	//Behaviour Classes
	//The MovementProposal class represents the shared proposal object exchanged between agents.
	//It stores the current round leader and the best movement offers (distance proposals) for each node.
	public static class MovementProposal implements Serializable {
		// Leader Agent ID
		public int roundLeaderId;
		//Map: NodeID -> Best Offer found so far
		public HashMap<Integer, Offer> bestOffers = new HashMap<>();

		public static class Offer implements Serializable {
			public int agentId;
			public double distance;
			//Creation of the proposal content
			public Offer(int agentId, double distance) {
				this.agentId = agentId;
				this.distance = distance;
			}

			@Override
			public String toString() {
				return "Ag" + agentId + "(" + distance + ")";
			}
		}
	}

	//Token Ring Behavior
	//It receives the vector, updates it, passes it on or decides the winner agent.
	private class TokenRingListenerBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			//Proposal for the Message Template
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			//It recieves the proposal
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				try {
					//Retrieves the content of the proposal
					MovementProposal proposal = (MovementProposal) msg.getContentObject();

					if (proposal.roundLeaderId == myId) {
						//That means that the token finished the loop and it's returned to the "leader" agent
						handleRoundCompletion(proposal);
					} else {
						//Intermediete Node | Update proposal content and pass the token to the next Agent
						calculateAndFillProposals(proposal);
						sendToken(proposal);
					}

				} catch (UnreadableException e) {
					e.printStackTrace();
				}
			} else {
				block();
			}
		}
		//One of the proposals has been accepted | The winner agent makes the move to the unvisited node.
		private void handleRoundCompletion(MovementProposal proposal) {
			//Find the absolute best move among all offers
			int bestNode = -1;
			int winningAgent = -1;
			double minTotalDist = Double.MAX_VALUE;
			//For every entry in the HashMap we compare based on the minimum total distance and find the winner agent
			for (Map.Entry<Integer, MovementProposal.Offer> entry : proposal.bestOffers.entrySet()) {
				if (entry.getValue().distance < minTotalDist) {
					minTotalDist = entry.getValue().distance;
					bestNode = entry.getKey();
					winningAgent = entry.getValue().agentId;
				}
			}
			//Print the winner agent
			if (bestNode != -1) {
				System.out.println(">>> DECISION: Agent " + winningAgent + " will move to Node " + bestNode + " (Dist: "
						+ minTotalDist + ")");

				//Instruct the winner to move
				ACLMessage cmd = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				cmd.addReceiver(new AID("Salesman-" + winningAgent, AID.ISLOCALNAME));
				//Simple content: Target Node ID + minTotalDist
				cmd.setContent(bestNode + "," + minTotalDist);
				send(cmd);
			} else {
				System.out.println("No reachable nodes left.");
			}
		}
	}

	//Move Execution Behavior
	//Listens for "ACCEPT_PROPOSAL" which means "You won the bid, move now"
	private class MoveCommandListenerBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				//Retrieve the targets node prop content and cost of distance
				String content = msg.getContent();
        		String[] parts = content.split(",");
				int targetNode = Integer.parseInt(parts[0]);
        		double moveCost = Double.parseDouble(parts[1]);
				//Update of accumulated distance
				totalDist += moveCost;
				//Agent moves to the target node and considers the target node as his new current node
				System.out.println(getLocalName() + " MOVING: " + currentLocation + " -> " + targetNode);
				currentLocation = targetNode;
				// The specific node has been visited, so we remove it from the unvisitedNodes list
				unvisitedNodes.remove(Integer.valueOf(targetNode));
				// We broadcast the removal to all the agents, so each agent can have the exact same snapshot of the map each round
				broadcastVisit(targetNode);

				//Start the next round
				//Wait small delay for broadcast to arrive
				myAgent.addBehaviour(new WakerBehaviour(myAgent, 1000) {
					protected void onWake() {
						startNewRound();
					}
				});

			} else {
				block();
			}
		}
	}

	//Listen for broadcasts about visited nodes (to keep lists in sync)
	private class VisitedListener extends CyclicBehaviour {
		@Override
		public void action() {
			//Recieve the Informal message
			MessageTemplate mt = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("node-visited")
        	);
			ACLMessage msg = myAgent.receive(mt);
			//Each agent recieves the content of the target node and removes it from the list
			if(msg != null){
				int targetNode = Integer.parseInt(msg.getContent());

				unvisitedNodes.remove(Integer.valueOf(targetNode));
			}
			else{
				block();
			}
			}
	}
	//Each Agent recieves the request to share their total distance
	private class StatsResponderBehaviour extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// Received a request for stats
				ACLMessage reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent(String.valueOf(totalDist)); // Send my local total distance
				send(reply);
			} else {
				block();
			}
		}
	}
	//Status Report for the total distance of all agents
	private class StatsCollectorBehaviour extends Behaviour {
		private int repliesReceived = 0;
		//Set the current agent's total distance
		private double globalTotalDist = totalDist; 
		private int expectedReplies = totalAgents - 1;

		@Override
		public void onStart() {
			//We send REQUEST to all the other agents
			ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
			for (int i = 0; i < totalAgents; i++) {
				if (i != myId) {
					request.addReceiver(new AID("Salesman-" + i, AID.ISLOCALNAME));
				}
			}
			send(request);
			System.out.println("Collecting results from other agents...");
		}
		//We recieve the total distances of each agent via INFORM Replies and we add them to a global value
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				double agentDist = Double.parseDouble(msg.getContent());
				globalTotalDist += agentDist;
				repliesReceived++;
			} else {
				block();
			}
		}

		@Override
		public boolean done() {
			return repliesReceived >= expectedReplies;
		}

		@Override
		public int onEnd() {
			System.out.println("------------------------------------------");
			System.out.println(">>> SIMULATION FINISHED <<<");
			System.out.println("Minimum Overall Distance Achieved (Global Optimization): " + globalTotalDist);
			System.out.println("------------------------------------------");
			return super.onEnd();
		}
}
}
