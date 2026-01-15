package src;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import java.util.Random;

public class LaunchMTSP {
    public static void main(String[] args) {
        //Firstly, we set the number of nodes and agents for the MTSP problem
        int numAgents = 3;
        int numNodes = 10;

        //Parse Arguments via cmd
        //Usage: java LaunchMTSP <num_agents> <num_nodes>
        if (args.length >= 2) {
            numAgents = Integer.parseInt(args[0]);
            numNodes = Integer.parseInt(args[1]);
        }
        System.out.println(">>> LAUNCHING MTSP SIMULATION");
        System.out.println(">>> Agents: " + numAgents);
        System.out.println(">>> Nodes:  " + numNodes);

        //We generate a common seed so all agents build the same map/enviroment
        long graphSeed = System.currentTimeMillis(); 
        Random rand = new Random(graphSeed);

        //We start the JADE Runtime instance and show the JADE GUI
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true"); 
        ContainerController cc = rt.createMainContainer(p);

        try {
            //For each agent
            for (int i = 0; i < numAgents; i++) {
                //We set a random start node for the current agent
                int startNode = rand.nextInt(numNodes);
                
                //Guarantee each agent makes the same env
                Object[] agentArgs = new Object[]{
                    String.valueOf(graphSeed),
                    String.valueOf(numNodes),
                    String.valueOf(numAgents),
                    String.valueOf(startNode) 
                };

                //Create and Start each Agent
                AgentController ac = cc.createNewAgent("Salesman-" + i, "src.MyAgent", agentArgs);
                ac.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
