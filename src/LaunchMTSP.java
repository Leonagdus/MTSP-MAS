package src;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import java.util.Random;

public class LaunchMTSP {
    public static void main(String[] args) {
        // Default values
        int numAgents = 3;
        int numNodes = 10;

        // 1. Parse Command Line Arguments
        // Usage: java jadelab2.LaunchMTSP <num_agents> <num_nodes>
        if (args.length >= 2) {
            numAgents = Integer.parseInt(args[0]);
            numNodes = Integer.parseInt(args[1]);
        }

        System.out.println(">>> LAUNCHING MTSP SIMULATION");
        System.out.println(">>> Agents: " + numAgents);
        System.out.println(">>> Nodes:  " + numNodes);

        // 2. Generate a common seed so all agents build the SAME map
        long graphSeed = System.currentTimeMillis(); 
        Random rand = new Random(graphSeed);

        // 3. Start JADE Runtime
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true"); // Show the JADE GUI
        ContainerController cc = rt.createMainContainer(p);

        try {
            // 4. Create Agents Dynamically
            for (int i = 0; i < numAgents; i++) {
                // Random start node for this agent (Source: [cite: 5])
                int startNode = rand.nextInt(numNodes);
                
                // Arguments passed to setup(): [Seed, MyID, NumNodes, TotalAgents, StartNode]
                Object[] agentArgs = new Object[]{
                    String.valueOf(graphSeed),
                    String.valueOf(numNodes),
                    String.valueOf(numAgents),
                    String.valueOf(startNode) 
                };

                // Create and Start Agent
                AgentController ac = cc.createNewAgent("Salesman-" + i, "src.MyAgent", agentArgs);
                ac.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}