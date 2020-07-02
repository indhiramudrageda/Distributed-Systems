/**
 * 
 */
package com.utd.aos.project.one;

import java.util.List;

/**
 * @author indhiramudrageda
 *
 */
public class SnapshotMessage extends Message {
	private static final long serialVersionUID = 1L;
	private List<LocalState> localState;
	
	public SnapshotMessage(int ID, List<LocalState> localState) {
		setID(ID);
		setLocalState(localState);
	}

	public List<LocalState> getLocalState() {
		return localState;
	}

	public void setLocalState(List<LocalState> localState) {
		this.localState = localState;
	}

}
