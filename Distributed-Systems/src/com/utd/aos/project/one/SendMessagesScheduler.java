/**
 * 
 */
package com.utd.aos.project.one;

/**
 * @author indhiramudrageda
 *
 */
public class SendMessagesScheduler implements Runnable {

	private Node node;
	public SendMessagesScheduler(Node node) {
		this.node = node;
	}

	@Override
	public void run() {
		if(node.getStatus().equalsIgnoreCase("Active"))
			node.sendAppMessage();
	}
}
