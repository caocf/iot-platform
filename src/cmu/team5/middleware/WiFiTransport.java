package cmu.team5.middleware;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class WiFiTransport implements Transportable
{
	private static final int portNum = 550;
	private TransportObserver observer;
	
	public WiFiTransport() {}
	
	public void addObserver(TransportObserver observer)
	{
		this.observer = observer;
	}

	public void send(String msg) {
	}
	
	public void startService() throws IOException
	{
		while(true)
		{
			ServerSocket listener = new ServerSocket(portNum);
			int clientNumber = 0;

    		try
    		{
    			while(true) {
    				System.out.println ("Waiting for connection on port " + portNum + "." );
    				new MessageHandler(listener.accept(), clientNumber++).start();
    			}
        	}
    		catch (IOException e)
        	{
        		System.err.println("Could not instantiate socket on port: " + portNum + " " + e);
        		System.exit(1);
        	}
    		
			System.out.println (".........................\n" );

    	}
	}
	
	private class MessageHandler extends Thread {
		private Socket socket;
		private int clientNumber;
		
		public MessageHandler(Socket socket, int clientNumber)
		{
			this.socket = socket;
			this.clientNumber = clientNumber;
			System.out.println("New connection with client# " + clientNumber + " at " + socket);
		}

		public void run() {
			String recvMsg;
			InputStream in = null;
			OutputStream out = null;

			try {
				in = socket.getInputStream();
				out = socket.getOutputStream();
				
				while(true) {
					recvMsg = Transport.getMessage(in);
					if (recvMsg == null) break;
	
					//System.out.println(">> " + recvMsg);

					observer.notify(new IoTMessage(recvMsg, out));
				}

			} catch (IOException e) {
				System.out.println("Error handling client# " + clientNumber + ": " + e);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					System.out.println("Couldn't close a socket, what's going on?");
				}
              System.out.println("Connection with client# " + clientNumber + " closed");
			}

		} // run
		
	} // class MessageHandler

}
