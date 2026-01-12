package jadelab2;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

public class MyAgent extends Agent {
	protected void setup () {

		// inizializzzation of the graph
		Graph<Integer, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		//services registration at DF
		DFAgentDescription dfad = new DFAgentDescription();
		dfad.setName(getAID());
		//service no 1
		ServiceDescription sd1 = new ServiceDescription();
		sd1.setType("travelling-salesman");
		sd1.setName("mtsp-agent");

		//add them all
		dfad.addServices(sd1);

		try {
			DFService.register(this,dfad);
		} catch (FIPAException ex) {
			ex.printStackTrace();
		}
		
		addBehaviour(new ListeningBehaviour(this));

		//doDelete();
	}
}

class ListeningBehaviour extends CyclicBehaviour
{

}