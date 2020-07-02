/**
 * 
 */
package com.utd.aos.project.one;

import java.util.List;

/**
 * @author indhiramudrageda
 *
 */
public class AppMessage extends Message {

	private static final long serialVersionUID = 1L;
	private List<Integer> vectorClock;
	
	public AppMessage(int ID, List<Integer> vectorClock) {
		setID(ID);
		setVectorClock(vectorClock);
	}

	public List<Integer> getVectorClock() {
		return vectorClock;
	}

	public void setVectorClock(List<Integer> vectorClock) {
		this.vectorClock = vectorClock;
	}

}
