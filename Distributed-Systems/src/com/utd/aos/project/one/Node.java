/**
 * 
 */
package com.utd.aos.project.one;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author indhiramudrageda
 *
 */
public class Node {

	private int ID;
	private String hostname;
	private int port;
	private String status;
	private int messagesSent;
	private int overallMessagesSent;
	private List<Node> neighbors;
	
	private int numberOfNodes;
	private int minPerActive;
	private int maxPerActive;
	private int msgsPerActive;
	private int minSendDelay;
	private int snapshotDelay;
	private int maxNumber;
	
	private List<Integer> vectorClock;
	private LocalState localState;
	private Set<Integer> monitoringMarkers;
	private Node[] adjList;
	private int parent;
	private List<Integer> children;
	private Set<LocalState> snapshotsRcvd;
	private Set<Integer> snapshotsFromChildren;
	
	private static final String CONFIG_FILE = "config.txt";
	private static final int SYNC = 0;
	
	public Node(int ID, String hostname, int port, String status) {
		setID(ID);
		setHostname(hostname);
		setPort(port);
		setStatus(status);
	}
	
	public Node(int ID, String hostname, int port, String status, int numberOfNodes, int minPerActive, int maxPerActive, int minSendDelay, int snapshotDelay, int maxNumber, List<Node> neighbors, Node[] adjList) {
		setID(ID);
		setHostname(hostname);
		setPort(port);
		setStatus(status);
		setNumberOfNodes(numberOfNodes);
		setMinPerActive(minPerActive);
		setMaxPerActive(maxPerActive);
		setMsgsPerActive(getRandomIntegerBetweenRange(minPerActive, maxPerActive));
		setMinSendDelay(minSendDelay);
		setSnapshotDelay(snapshotDelay);
		setMaxNumber(maxNumber);
		setNeighbors(neighbors);
		monitoringMarkers = new HashSet<>();
		snapshotsRcvd = new HashSet<>();
		snapshotsFromChildren = new HashSet<>();
		setAdjList(adjList);
		initializeVectorClock();
		
		//construct spanning tree
		constructSpanningTrees(adjList);
		
		//scheduler to keep sending messages to neighbors periodically.
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(); 
		scheduler.scheduleAtFixedRate(new SendMessagesScheduler(this), 2000, getMinSendDelay(), TimeUnit.MILLISECONDS);
	
		//Listener to receive messages
		MessageListener messageListener = new MessageListener(this);
		messageListener.start();
		
		if(getID() == SYNC) {
			ScheduledExecutorService snapshotScheduler = Executors.newSingleThreadScheduledExecutor(); 
			snapshotScheduler.scheduleAtFixedRate(new SnapshotScheduler(this), 2000, getSnapshotDelay(), TimeUnit.MILLISECONDS);
		}
	}
	
	private void constructSpanningTrees(Node[] adjList) {
		Deque<Integer> queue = new LinkedList<>();
		int n = getNumberOfNodes();
		boolean[] visited = new boolean[n];
		int[] parents = new int[n];
		
		@SuppressWarnings("unchecked")
		List<Integer>[] children = new LinkedList[n];
		
		queue.offer(0);
		visited[0] = true;
		while(!queue.isEmpty()) {
			int curr = queue.poll();
			for(Node child : adjList[curr].getNeighbors()) {
				int childID = child.getID();
				if(!visited[childID]) {
					parents[childID] = curr;
					if(children[curr] == null) children[curr] = new LinkedList<Integer>();
					children[curr].add(childID);
					queue.offer(childID);
					visited[childID] = true;
				}
			}
		}
		setParent(getID() == 0 ? -1 : parents[getID()]);
		setChildren(children[getID()]);
	}

	protected synchronized void sendAppMessage() {
		updateVectorClockBeforeSend();
		Message message = new AppMessage(getID(), getVectorClock());
		Node neighbor = neighbors.get(getRandomIntegerBetweenRange(0, neighbors.size()-1));
		send(message, neighbor.getID(), neighbor.getHostname(), neighbor.getPort());
		increamentMessagesSent();
		if(getOverallMessagesSent() == getMaxNumber() || getMessagesSent() == getMsgsPerActive()) setStatus("Passive");
	}
	
	protected synchronized void receiveAppMessage(AppMessage message) {
		//check for in-transit msgs and update the localState.
		if(getLocalState() != null && this.monitoringMarkers.contains(message.getID())) getLocalState().incrementInTransit();
		
		if(getStatus().equalsIgnoreCase("Passive") && getOverallMessagesSent() < getMaxNumber()) {
			setStatus("Active");
			setMessagesSent(0);
		}
		
		//update vector clock.
		updateVectorClockAfterRcv(message.getVectorClock());
	}
	
	protected void sendMarkerMessage()  {
		for(Node neighbor : getNeighbors()) 
			send(new MarkerMessage(getID()), neighbor.getID(), neighbor.getHostname(), neighbor.getPort());
	}
	
	protected synchronized void recordLocalState() {
		if(getLocalState() != null) return;
		//record local state
		setLocalState(new LocalState(getID(), getStatus(), new ArrayList<>(getVectorClock()), 0));
		//send marker messages to all its neighbors
		sendMarkerMessage();
		//output recorded state
		StringBuilder output = new StringBuilder();
		for(int i=0;i<getNumberOfNodes();i++) 
			output.append(getLocalState().getVectorClock().get(i)).append(" ");
		output.append("\n");
	    try {
	    	BufferedWriter writer = new BufferedWriter(new FileWriter("config-"+getID()+".out", true));
			writer.append(output.toString());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected synchronized void receiveMarkerMessage(MarkerMessage message) {
		//check if localState is null. 
		if(getLocalState() == null) {
			recordLocalState();
			
			//monitor all other neighbors
			for(Node neighbor : neighbors) {
				if(neighbor.getID() == message.getID()) continue;
				this.monitoringMarkers.add(neighbor.getID());
			}
		} else {
			this.monitoringMarkers.remove(Integer.valueOf(message.getID()));
		}
		
		//if no more monitoring is needed and snapshot has been received by all children, converge-cast snapshot to node 0.
		sendSnapshotMessage();
	}
	
	private synchronized void sendSnapshotMessage() {
		if(getID() != SYNC && this.monitoringMarkers.size() == 0 && getSnapshotsFromChildren().size() == getChildren().size()) {
			List<LocalState> collectedSnapshots = new ArrayList<>();
			collectedSnapshots.add(getLocalState());
			collectedSnapshots.addAll(getSnapshotsRcvd());
			send(new SnapshotMessage(getID(), collectedSnapshots), getAdjList()[parent].getID(), getAdjList()[parent].getHostname(), getAdjList()[parent].getPort());
			setLocalState(null);
			getSnapshotsRcvd().clear();
			getSnapshotsFromChildren().clear();;
		}
	}
	
	protected synchronized void receiveSnapshotMessage(SnapshotMessage message) throws IOException {
		getSnapshotsRcvd().addAll(message.getLocalState());
		getSnapshotsFromChildren().add(message.getID());
		if(getID() == SYNC) {
			if(getSnapshotsFromChildren().size() == getChildren().size()) {
				// Print if global snapshot is consistent
				List<LocalState> globalState = new ArrayList<>(getSnapshotsRcvd());
				globalState.add(getLocalState());
				System.out.println("Is Snapshot consistent: " + isSnapshotConsistent(globalState));
				
				// check termination detection
				if(isTerminationDetected(globalState)) {
					System.out.println("Termination detected!!");
					sendTerminationMessage();
				}
				
				setLocalState(null);
				getSnapshotsRcvd().clear();
				getSnapshotsFromChildren().clear();
			}
		} else {
			//if no more monitoring is needed and snapshot has been received by all children, converge-cast snapshot to parent.
			sendSnapshotMessage();
		}
	}
	
	private void sendTerminationMessage() {
		for(int child : getChildren()) 
			send(new TerminationMessage(getID()), getAdjList()[child].getID(), getAdjList()[child].getHostname(), getAdjList()[child].getPort());
		System.exit(0);
	}
	
	protected void receiveTerminationMessage(TerminationMessage message) {
		sendTerminationMessage();
	}

	private boolean isTerminationDetected(List<LocalState> localStates) {
		for(LocalState state : localStates) {
			if(!state.getStatus().equalsIgnoreCase("Passive") || state.getInTransit() > 0) return false;
		}
		return true;
	}

	private boolean isSnapshotConsistent(List<LocalState> localStates) {
		int n = getNumberOfNodes();
		int[] Vmax = new int[n];
		for(int i=0;i<n;i++) {
			LocalState ls = localStates.get(i);
			for(int j=0;j<getNumberOfNodes();j++)
				Vmax[j] = Math.max(Vmax[j], ls.getVectorClock().get(j));
		}
		
		for(int i=0;i<getNumberOfNodes();i++) {
			LocalState ls = localStates.get(i);
			if(ls.getVectorClock().get(ls.getNodeID()) != Vmax[ls.getNodeID()]) return false;
		}
		
		return true;
	}

	private void send(Message message, int destID, String destHost, int destPort) {
		Socket s = null;
		OutputStream os = null;
		ObjectOutputStream oos = null;
		try {
			s = new Socket(InetAddress.getByName(destHost).getHostAddress(), destPort);
			//s = new Socket("127.0.0.1", destPort);
			os = s.getOutputStream();
			oos = new ObjectOutputStream(os);
			oos.writeObject(message);
		} catch (IOException e) {
			System.out.println("Error sending data to "+destHost+": "+ e.getMessage());
		} 
	}
	
	public static void main(String[] args) {
		int numberOfNodes = 0;
		int minPerActive = 0;
		int maxPerActive = 0;
		int minSendDelay = 0;
		int snapshotDelay = 0;
		int maxNumber = 0;
		int ID = 0;
		
		String[] possibleStatus = {"Active", "Passive"};
		
		try (BufferedReader br = new BufferedReader(new FileReader(CONFIG_FILE))) {
			String currentLine;
			int n = 0;
			InetAddress inetAddress = InetAddress.getLocalHost();
			while ((currentLine = br.readLine()) != null) {
				currentLine = preprocessConfig(currentLine);
				if(currentLine.length() == 0) continue; //invalid line
				String[] params = currentLine.split("\\s+");
				numberOfNodes = Integer.parseInt(params[0]);
				minPerActive = Integer.parseInt(params[1]);
				maxPerActive = Integer.parseInt(params[2]);
				minSendDelay = Integer.parseInt(params[3]);
				snapshotDelay = Integer.parseInt(params[4]);
				maxNumber = Integer.parseInt(params[5]);
				n = numberOfNodes;
				break;
			}
			Node[] nodeList = new Node[numberOfNodes];
			while(n > 0) {
				currentLine = preprocessConfig(br.readLine());
				if(currentLine.length() == 0) continue; //invalid line
				String[] params = currentLine.split("\\s+");
				int currID = Integer.parseInt(params[0]);
				String currHost = params[1]+".utdallas.edu";
				int currPort = Integer.parseInt(params[2]);
				
				if(inetAddress.getHostAddress().equals(InetAddress.getByName(currHost).getHostAddress())) ID = currID;
				nodeList[currID] = new Node(currID, currHost, currPort, possibleStatus[getRandomIntegerBetweenRange(0, 1)]);
				n--;
			}
			
			nodeList[0].setStatus("Active");
			n = numberOfNodes;
			List<Node> currNeighbors = new ArrayList<>();
			while(n > 0) {
				currentLine = preprocessConfig(br.readLine());
				if(currentLine.length() == 0) continue; //invalid line
				String[] params = currentLine.split("\\s+");
				List<Node> neighbors = new ArrayList<>();
				for(String param : params) 
					neighbors.add(nodeList[Integer.parseInt(param)]);
				nodeList[numberOfNodes-n].setNeighbors(neighbors);
				if(numberOfNodes-n == ID) currNeighbors = new ArrayList<>(neighbors);
				n--;
			}
			
			System.out.println("Starting node:" + ID);
			new Node(ID, nodeList[ID].getHostname(), nodeList[ID].getPort(), nodeList[ID].getStatus(), numberOfNodes, minPerActive, maxPerActive, minSendDelay, snapshotDelay, maxNumber, currNeighbors, nodeList);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int getID() {
		return ID;
	}

	private void setID(int ID) {
		this.ID = ID;
	}

	public String getHostname() {
		return hostname;
	}

	private void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	private void setPort(int port) {
		this.port = port;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getMessagesSent() {
		return messagesSent;
	}

	public void setMessagesSent(int messagesSent) {
		this.messagesSent = messagesSent;
	}
	
	public void increamentMessagesSent() {
		int currSent = getMessagesSent();
		setMessagesSent(currSent+1);
		increamentOverallMessagesSent();
	}

	public int getOverallMessagesSent() {
		return overallMessagesSent;
	}

	public void setOverallMessagesSent(int overallMessagesSent) {
		this.overallMessagesSent = overallMessagesSent;
	}
	
	public void increamentOverallMessagesSent() {
		int currSent = getOverallMessagesSent();
		setOverallMessagesSent(currSent+1);
	}

	public List<Node> getNeighbors() {
		return neighbors;
	}

	public void setNeighbors(List<Node> neighbors) {
		this.neighbors = neighbors;
	}

	public List<Integer> getVectorClock() {
		return vectorClock;
	}

	public void setVectorClock(List<Integer> vectorClock) {
		this.vectorClock = vectorClock;
	}
	
	public LocalState getLocalState() {
		return localState;
	}

	public void setLocalState(LocalState localState) {
		this.localState = localState;
	}

	public int getNumberOfNodes() {
		return numberOfNodes;
	}

	public void setNumberOfNodes(int numberOfNodes) {
		this.numberOfNodes = numberOfNodes;
	}

	public int getMinPerActive() {
		return minPerActive;
	}

	public void setMinPerActive(int minPerActive) {
		this.minPerActive = minPerActive;
	}

	public int getMaxPerActive() {
		return maxPerActive;
	}

	public void setMaxPerActive(int maxPerActive) {
		this.maxPerActive = maxPerActive;
	}

	public int getMsgsPerActive() {
		return msgsPerActive;
	}

	public void setMsgsPerActive(int msgsPerActive) {
		this.msgsPerActive = msgsPerActive;
	}

	public int getMinSendDelay() {
		return minSendDelay;
	}

	public void setMinSendDelay(int minSendDelay) {
		this.minSendDelay = minSendDelay;
	}

	public int getSnapshotDelay() {
		return snapshotDelay;
	}

	public void setSnapshotDelay(int snapshotDelay) {
		this.snapshotDelay = snapshotDelay;
	}

	public int getMaxNumber() {
		return maxNumber;
	}

	public void setMaxNumber(int maxNumber) {
		this.maxNumber = maxNumber;
	}
	
	public Set<Integer> getMonitoringMarkers() {
		return monitoringMarkers;
	}

	public void setMonitoringMarkers(Set<Integer> monitoringMarkers) {
		this.monitoringMarkers = monitoringMarkers;
	}

	public Node[] getAdjList() {
		return adjList;
	}

	public void setAdjList(Node[] adjList) {
		this.adjList = adjList;
	}

	public int getParent() {
		return parent;
	}

	public void setParent(int parent) {
		this.parent = parent;
	}

	public List<Integer> getChildren() {
		return children;
	}

	public void setChildren(List<Integer> children) {
		this.children = children == null ? new LinkedList<>() : children;
	}

	public Set<LocalState> getSnapshotsRcvd() {
		return snapshotsRcvd;
	}

	public void setSnapshotsRcvd(Set<LocalState> snapshotsRcvd) {
		this.snapshotsRcvd = snapshotsRcvd;
	}

	public Set<Integer> getSnapshotsFromChildren() {
		return snapshotsFromChildren;
	}

	public void setSnapshotsFromChildren(Set<Integer> snapshotsFromChildren) {
		this.snapshotsFromChildren = snapshotsFromChildren;
	}

	private void initializeVectorClock() {
		this.vectorClock = new ArrayList<>();
		for(int i=0;i<getNumberOfNodes();i++)
			this.vectorClock.add(0);
	}
	
	private void updateVectorClockBeforeSend() {
		int curr = this.vectorClock.get(getID());
		this.vectorClock.set(getID(), curr+1);
	}
	
	private void updateVectorClockAfterRcv(List<Integer> rcvdClock) {
		int n = this.vectorClock.size();
		for(int i=0;i<n;i++) {
			this.vectorClock.set(i, Math.max(this.vectorClock.get(i), rcvdClock.get(i)));
		}
		int curr = this.vectorClock.get(getID());
		this.vectorClock.set(getID(), curr+1);
	}
	
	private static String preprocessConfig(String currentLine) {
		currentLine = currentLine.trim();
		if(currentLine.length() == 0 || !Character.isDigit(currentLine.charAt(0))) return "";
		int comment = currentLine.indexOf('#');
		currentLine = comment == -1 ? currentLine : currentLine.substring(0, comment) ;
		return currentLine;
	}
	
	public static int getRandomIntegerBetweenRange(double min, double max){
	    double random = (int)(Math.random()*((max-min)+1))+min;
	    return (int) random;
	}
}
