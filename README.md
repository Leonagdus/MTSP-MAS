# Multiple Travelling Salesman Problem with Multi-Agent System Approach

A distributed solution to the **Multiple Travelling Salesman Problem (MTSP)** using a **Multi-Agent System (MAS)** implemented in Java with the JADE framework. Agents autonomously coordinate to visit all locations on a weighted graph while minimizing total travel distance.

---

## 📋 Problem Description

A set of salesmen (agents) are equipped with a map of locations that is represented as a weighted graph. The graph **G=(V,E)** contains the available locations and the distance between two locations.

- Each agent starts from an **initial randomized location** inside the map and searches for the next unvisited location in each round
- The goal is **all locations to be visited once** by the group of the agents which are navigating the map and find the **overall minimum distance**

---

## 🎯 Strategy

Each step inside the map is determined by which agent's proposal has been accepted on the current round.

**Initialization Phase**:
- Each agent is placed in a **random initial location**, based on a shared seed
- Every agent calculates the **shortest path** from its current location to every unvisited node, using **Dijkstra's Algorithm**

**Proposal & Token Phase**:
- Once an agent calculates all the distances, it creates **proposals for each unvisited node** and shares the distance
- All of these proposals are passed to a **token that circulates** through all the agents
- The agent who starts the round (the **"leader"**) initially holds the token and initiates it

**Token Update & Circulation**:
- After the update, the token is passed to the next agent who compares its own distances with the current **"best" distance/offer**
- The **token updates** only when the distance of the agent that holds the token is less than the **"best" proposal** of some other agent
- The token passes all the agents until it reaches the **"leader" agent** who looks at all the proposals and picks the one with the **shortest distance**

**Movement & Synchronization**:
- The **winner agent** (with his accepted proposal) moves to the selected node
- The **unvisited list is updated** and the node that the agent which is currently moved is removed
- The process continues to the **next round**

**Communication Protocol**:
- For the communication between the agents, the **FIPA-Propose Interaction Protocol** is being used

---

## 🔧 Graph Creation

For this project we choose the **Simple Weighted Graph**, which means that every edge in the graph has a specific weight.

- The **number of vertices** is going to be initialized at the start of the program
- For the creation of edges with weights we used the popular **Dijkstra's Algorithm**, so we can measure the weights straight-forward
- **Important**: The graph needs to be **complete** for this specific type of problem (MTSP). We need to ensure that every vertex is reachable from every other vertex
- Each agent retrieves a **random seed** that is the same for the whole MAS system, because we want the agents to work on the same environment/map

---

## 💻 Code Explanation

### `LaunchMTSP.java`

The class `LaunchMTSP` is the **main class** of the project and handles the logic behind the MTSP structure.

- Firstly, the **command-line arguments** (`numAgents`, `numNodes`) are initialized with **default values** and then parsed from the command line if provided
- The **random graphSeed** has been set based on the **current time in (ms)**
- Next, the class **starts the runtime instance of JADE (GUI)**
- For every agent the code sets a **random start node** and pass it to the agent's arguments with the `graphSeed`, the number of `nodes` and the number of `agents`
- Finally, the **AgentController** creates and starts the new agent for each loop

---

### `MyAgent.java`

The class `MyAgent` contains all the **helper functions** from creating the proposal to calculate the minimum distance and save the best Proposal.

**Global Variables Initialization**:
- Initially, the global variables (`AgentIDs`, the `graph`, `unvisited node list` and `metrics`) were initialized
- A **broadcastVisit function** was implemented to inform all agents about the current location of an agent and to notify them of the removal of a node that the winning agent is going to navigate

**Setup Method**:
- The **arguments** from the current agent are received
- **Variables** with default values have been initialized
- The **arguments** are parsed and passed to the current agent's instance variables
- With the function **generateRandomGraph()** that is going to be explained later, the **simple weighted graph** is created
- After, an **ArrayList** was created and initialized for the **unvisited nodes** of the graph. Each starting point is considered already visited
- **Retrieval** of the local name and AID of the current agent and fetching of the next agent's ID for the purpose of the **ring topology (token circulation)**
- The **registration** of the MTSP-Agent service follows next (the function `registerService()` will be explain later)

**Project Behaviours**:
The project contains **7 behaviours** for each purpose:

1. **TokenRingListenerBehaviour()** - Either updates the token (content of the proposal) or determines the winner agent
2. **MoveCommandListenerBehaviour()** - Awaits for a ACCEPT_PROPOSAL message and handles the calculation of the total distance and the update of the unvisitedNodes list with removing the target node
3. **VisitedListener()** - Listens for broadcast messages about the visited nodes. This way every agent's list is in sync
4. **StatsCollectorBehaviour()** - Creates a status report (via printed messages) for the minimum overall distance that the whole group of agents achieved
5. **StatsResponderBehaviour()** - Guarantees that each Agent receives the request from StatsCollectorBehaviour for sharing their local total distance
6. **WakerBehaviour (Broadcast Sync)** - Slightly delays the broadcast of the starting points of each agent. **IMPORTANT**: This delay ensures that all the Agents are in sync and acknowledge each other's starting points, so they remove each node of the starting point from the unvisitedNodes list
7. **WakerBehaviour (Round Delay)** - Waits 2 seconds before each round starts

---

## 🛠️ Helper Methods

### `generateRandomGraph()`

- Initially, the **graph is being created and initialized** using the `SimpleWeightedGraph<>(DefaultWeightedEdge.class)` class from java's library **JGraphT**
- A **random seed** was created using the `Random` class for setting random edge weights
- Using the **addVertex()** method the vertices were created
- By comparing two neighboring nodes, an **edge is created** at each iteration, and its weight is assigned a **random value** within the range **[10, 100]**
- Using the **addEdge()** method we create each edge and with the **setEdgeWeight()** method we set the random weight value to the specific edge
- Finally, we **return the graph**

### `registerService()`

- The **DFAgentDescription** and **ServiceDescription** has been created
- The current agent's **AID** is imported to the DFAgentDescription object
- In the service object the **type** `"travelling-salesman"` and **name** `"mtsp-agent"` of the service has been inserted
- Lastly, the service `"mtsp-agent"` is added to the current agent description object and the dfad has been registered

### `startNewRound()`

The `startNewRound()` function is called by the **"leader" agent** who announces the minimum distance and starts a new round with a token containing his weights.

- Firstly, if **all the nodes have already been visited** it prints a message and the total local distance of one of the agents
- For the **minimum overall distance** the function calls the behaviour `StatsCollectorBehaviour` that retrieves all of the local total distances of each agent to finally present the **overall minimum total distance** of the path
- If there are **still unvisited nodes**, the function **starts a new round**, creates a proposal, saves the AID of the **"leader" agent**, calculates the current agent distances with `calculateAndFillProposals()` and sends the token with the proposal to the next agent (with `sendToken()`)

### `calculateAndFillProposals()`

The function `calculateAndFillProposals()` **calculates distances** from current location to all unvisited nodes and updates the proposal.

- For the **calculation of the minimum distance** between the current location and the target unvisited nodes the **Dijkstra's Algorithm** is being used
- Additionally, if the **path is not empty**, the weights of the current path are retrieved. If the current location coincides with the target node, the distance is set to zero
- Finally, the **agents' proposals are compared**, and based on the **computed distances**, the **best proposal is selected**

### `sendToken()`

The function `sendToken()` **sends the token** with the content of the proposal to the next available agent.

- Initially, the **message with PROPOSE type** has been created
- The **receiver** of the message is the next agent
- The **message** is initialized with the content of the proposal
- Finally, the **message** is being sent

### `createGraph()` & `addEdge()`

The function `createGraph()` & `addEdge()` is used for **debugging purposes** in case the `generateRandomGraph` does not work.

- In this function the **edges were initialized** with picked weights
- Those 2 functions has been **commented**, because the project works perfectly with the `generateRandomGraph()` function

---

## 🎭 Behaviour Classes

### `MovementProposal`

The `MovementProposal` class represents the **shared proposal object** exchanged between agents. It stores the **current round leader** and the **best movement offers** (distance proposals) for each node.

- The function begins with the **creation of the leader agent ID** and the **HashMap** that keeps the `bestOffers` so far
- A **static class** named `Offer` keeps the **agent ID** of the agent who made the proposal and his **total distance** (currentLocation <-> targetNode)

### `TokenRingListenerBehaviour` / `action()`

The `TokenRingListenerBehaviour` class **either updates the token** (content of the proposal) or **determines the winner agent**.

- At the beginning the **current agent receives the token** from the previous agent and retrieves the content of the proposal
- If the token **returned to the "leader" agent** than the `handleRoundCompletion()` function is called to handle the end of the round
- The **intermediate nodes update the proposal's content** and pass the token to the next agent

### `TokenRingListenerBehaviour` / `handleRoundCompletion()`

The `TokenRingListenerBehaviour`'s method `handleRoundCompletion()` is called when:
- **One of the proposals has been accepted** and the winner agent needs to make the move to the unvisited node

**Process**:
- Initially, the variables `bestNode`, `winningAgent` and `minTotalDist` have taken **default values**
- For every **entry inside the HashMap bestOffers** we check the distance and we save the **best node**, the **winning agent** and the **minimum total distance**
- To **instruct the winner to move**, a proposal has been sent with the content of the **bestNode** and the **minimum total distance**

### `MoveCommandListenerBehaviour`

The behaviour `MoveCommandListenerBehaviour` **handles the move execution** behaviour of the winner agent.

- Initially, the **agent receives the proposal** (ACCEPT_PROPOSAL Message) from the `handleRoundCompletion()` function
- It **retrieves the content of the proposal** and splits it into 2 parts
- Those 2 parts were assigned to the `targetNode` and `moveCost` variables
- The **moveCost** for each winner agent is added to the `totalDist` variable
- The **current location updates** and now the `targetNode` is the new current node for the winner agent
- The **specific targetNode is being removed** from the `unvisitedNodes` list
- The **removal of the targetNode** is being broadcasted the whole group of the agents
- A **slight delay** occurs before the new round starts
- If the **content of the proposal is null** block the whole `action()`

### `VisitedListener`

The class `VisitedListener` **listens for broadcast messages** (INFORM Messages) about new visited nodes (to keep the `unvisitedNodes` lists for each agent in sync).

- After each agent **receives the INFORM message** and match the ConvID, it **retrieves the content of the message** and removes the target node that has been broadcast from the `unvisitedNodes` list
- If the **message is null**, then the whole process is blocked

### `StatsResponderBehaviour`

The behaviour `StatsResponderBehaviour` **handles the requests** that all the agents receive to share their total local distance.

- This class is **directly connected** with the `StatsCollectorBehaviour`
- Firstly, each agent **receives the REQUEST message** and if the content of the message isn't null, then they **create a reply INFORM message** with the content of their **total local distance** (`totalDist`)

### `StatsCollectorBehaviour`

The behaviour `StatsCollectorBehaviour` **creates a "status report"** to estimate the minimum overall distance of the whole path.

**Initialization**:
- At the start, three variables have been initialized:
  - `repliesRecieved` - How many replies have been received
  - `globalTotalDist` - The overall minimum at first it is initialized to the current agent's local total distance
  - `expectedReplies` - The expected replies are `totalAgents-1`, because the total distance of the current agent that has called this behaviour is already claimed

**`onStart()` Method**:
- In this method **REQUEST messages** are sent to all the agents, except the current agent
- Those requests are heading to the `StatsResponderBehaviour` class that handles the reply messages of total distances

**`action()` Method**:
- In this method the **messages have been received** and each total local distance is **added to the globalTotalDist** variable
- Additionally, the **replies counter** has been increased for each reply

**`done()` / `onEnd()` Methods**:
- The method `done()` **validates** that all the replies have been received and the whole calculation is done
- The method `onEnd()` **prints an output message** and the **minimum overall distance** that has been achieved from all the agents

---

## 📊 Simulation of MTSP

- Initially, each **Salesman/Agent** is initialized to a **randomized node** in the Map
- The **simulation starts running**. A random agent starts the first round
- The map contains **[1,2,5,6,7,8,9] unvisited nodes**
- In the background the **total distance** is calculated from each agent and a **winner agent** is being announced
- At the same time the **target node** is removed from the `unvisitedNodes` list
- The **output** also prints the **total local distance** that each agent achieved on each round
- The **procedure repeats** until all the nodes have been visited
- Once the `unvisitedNode` list is empty, the agent who finished the last round prints his total local distance
- Using the `StatsCollectorBehaviour` class all the **total distances** have been received and the **minimum overall distance** is calculated and printed

---

## 🤝 Team

**Team Members**:
- Koprintziotis Panagiotis
- Grano Giuseppe
- Cardenas Peralta Leo

---

## 📚 Technologies & Libraries

| Technology | Purpose |
|-----------|---------|
| **Java** | Core language |
| **JADE** | Multi-agent framework |
| **JGraphT** | Graph data structure |

---

## 📝 License

This project is provided as-is for educational purposes.

---

**Last Updated**: January 2026  
**Status**: ✅ Complete
