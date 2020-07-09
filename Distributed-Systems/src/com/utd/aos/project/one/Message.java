/**
 * 
 */
package com.utd.aos.project.one;

import java.io.Serializable;

/**
 * @author indhiramudrageda
 *
 */
public class Message implements Serializable{
	private static final long serialVersionUID = 1L;
	private int ID;
	
	public Message() {
		
	}

	public int getID() {
		return ID;
	}

	public void setID(int ID) {
		this.ID = ID;
	}
}
