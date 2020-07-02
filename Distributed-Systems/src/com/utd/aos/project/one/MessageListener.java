/**
 * 
 */
package com.utd.aos.project.one;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author indhiramudrageda
 *
 */
public class MessageListener extends Thread{ 
	private InputStream inStream;
    private ObjectInputStream objectInputStream; 
    private ServerSocket tcpServersocket; 
    private Socket socket;
    private final Node node;
    
	public MessageListener(Node node) {
		this.node = node;
		try {
			tcpServersocket = new ServerSocket(node.getPort());
		} catch (IOException e) {
			System.out.println("Error creating server socket: "+ e.getMessage());
		}
	}
	
	@Override
    public void run()  
    { 
        while (true)  
        { 
            try { 
            	// receive updates or requests from neighboring fog nodes.
            	socket = tcpServersocket.accept();
            	inStream = socket.getInputStream();
        		objectInputStream = new ObjectInputStream(inStream); 
                Object obj = objectInputStream.readObject();
                if(obj instanceof MarkerMessage) {
                	this.node.receiveMarkerMessage((MarkerMessage) obj);
				} else if(obj instanceof SnapshotMessage) {
					this.node.receiveSnapshotMessage((SnapshotMessage) obj);
				} else if(obj instanceof AppMessage){
					this.node.receiveAppMessage((AppMessage) obj);
				} else {
					this.node.receiveTerminationMessage((TerminationMessage) obj);
				}
            } 
            catch (IOException | ClassNotFoundException e) { 
            	System.out.println("Error reading message sent to :" + node.getID()+e.getMessage()); 
            	System.out.println("Error reading message sent to :" + node.getID()+e.getStackTrace()); 
            	try {
					inStream.close();
					objectInputStream.close();
					socket.close();
				} catch (IOException e1) {
					System.out.println("Error closing connections: "+ e1.getMessage());
				}
            } 
        }           
    }
}
