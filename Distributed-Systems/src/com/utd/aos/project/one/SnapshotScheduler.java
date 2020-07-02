/**
 * 
 */
package com.utd.aos.project.one;

/**
 * @author indhiramudrageda
 *
 */
public class SnapshotScheduler implements Runnable {

	private Node node;
	public SnapshotScheduler(Node node) {
		this.node = node;
	}

	@Override
	public void run() {
		node.sendMarkerMessage();
	}
}
