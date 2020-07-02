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
		// TODO Auto-generated constructor stub
	}

	public int getID() {
		return ID;
	}

	public void setID(int ID) {
		this.ID = ID;
	}
}
