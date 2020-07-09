/**
 * 
 */
package com.utd.aos.project.one;

import java.io.Serializable;
import java.util.List;

/**
 * @author indhiramudrageda
 *
 */
public class LocalState implements Serializable{
	private static final long serialVersionUID = 1L;
	private int nodeID;
	private String status;
	private int inTransit;
	private List<Integer> vectorClock;
	
	public LocalState(int nodeID, String status, List<Integer> vectorClock, int inTransit) {
		setNodeID(nodeID);
		setStatus(status);
		setVectorClock(vectorClock);
		setInTransit(inTransit);
	}

	public int getNodeID() {
		return nodeID;
	}

	public void setNodeID(int nodeID) {
		this.nodeID = nodeID;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getInTransit() {
		return inTransit;
	}
	
	public void incrementInTransit() {
		int currSent = getInTransit();
		setInTransit(currSent+1);
	}

	public void setInTransit(int inTransit) {
		this.inTransit = inTransit;
	}

	public List<Integer> getVectorClock() {
		return vectorClock;
	}

	public void setVectorClock(List<Integer> vectorClock) {
		this.vectorClock = vectorClock;
	}

	@Override
	public boolean equals(Object o) {
		LocalState ls = (LocalState)o;
		return this.nodeID == ls.nodeID;
	}
}
