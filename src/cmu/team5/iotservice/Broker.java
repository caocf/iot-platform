package cmu.team5.iotservice;

import java.io.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import cmu.team5.middleware.*;

enum DeviceType { UNKNOWN, NODE, TERMINAL };

public class Broker {
	
	private static final int portNum = 550;
	private static final int MAXQSIZE = 1024;
	private NodeManager nodeMgr;
	private TerminalManager terminalMgr;
	private BlockingQueue<IoTMessage> msgQ;
	private Transport transport;
	
	public Broker()
	{
		nodeMgr = new NodeManager();
		terminalMgr = new TerminalManager();
		
		msgQ = new ArrayBlockingQueue<IoTMessage>(MAXQSIZE);
		transport = new Transport(msgQ);
	}
	
	private IoTMessage getQueue() {
		IoTMessage message = null;
		
		synchronized(msgQ) {
			try {
				message = msgQ.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		return message;
	}
	
	public void startService() throws IOException
	{
		IoTMessage iotMsg = null;
		String message = null;
		DeviceType deviceType = DeviceType.UNKNOWN;
		String deviceKey;

		transport.startService();
		
		while(true) {
			iotMsg = getQueue();
			message = iotMsg.getMessage();
			
			System.out.println(">> " + message);
			
			String deviceTypeStr = Protocol.getDeviceType(message);
			if (deviceTypeStr != null) {
				OutputStream out = iotMsg.getStream();
				
				if (deviceTypeStr.equals("node")) {

					deviceType = DeviceType.NODE;
					deviceKey = Protocol.getNodeId(message);
					nodeMgr.addNode(deviceKey, out);
				} else if (deviceTypeStr.equals("terminal")) {
					deviceType = DeviceType.TERMINAL;
					deviceKey = Protocol.getUserId(message);
					terminalMgr.addTerminal(deviceKey, out);
				} else {
					System.out.println("Not a node or terminal device so close the connection");
					out.close();
					return;
				}
				
				continue;
			}
			
			String messageType = Protocol.getMessageType(message);
			if (messageType != null && messageType.equals("sensor")) {
				nodeMgr.handleNodeMsg(
						Protocol.getNodeId(message),
						Protocol.getSensorType(message),
						Protocol.getSensorValue(message));
			}
	
			if (messageType != null && messageType.equals("command")) {
				nodeMgr.sendCommandMsg(Protocol.getNodeId(message), message);
			}
		}
		
		/*
		try {
			startServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
	}

	private void startServer() throws IOException
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
		private DeviceType deviceType = DeviceType.UNKNOWN;
		private String deviceKey;
		
		public MessageHandler(Socket socket, int clientNumber)
		{
			this.socket = socket;
			this.clientNumber = clientNumber;
			System.out.println("New connection with client# " + clientNumber + " at " + socket);
		}
				
		public void run() {
			String message;
			byte[] buffer = new byte[1024];
			int readBytes, leftBytes, totalBytes, msgLength;
			
			try {
				InputStream in = socket.getInputStream();
				OutputStream out = socket.getOutputStream();
				
				msgLength = Transport.getMessageLength(in);
				if (msgLength < 0) return;
				
				leftBytes = msgLength;
				readBytes = 0;
				totalBytes = 0;
				while(leftBytes > 0) {
					readBytes = in.read(buffer, totalBytes, leftBytes);
					System.out.println("readBytes: " + readBytes);
					if (readBytes < 0) return;
					leftBytes -= readBytes;
					totalBytes += readBytes;
				}
				
				message = new String(buffer, 0, msgLength);

				System.out.println(">> " + message);
				String deviceTypeStr = Protocol.getDeviceType(message);
				if (deviceTypeStr != null && deviceTypeStr.equals("node")) {
					deviceType = DeviceType.NODE;
					deviceKey = Protocol.getNodeId(message);
					nodeMgr.addNode(deviceKey, out);
				} else if (deviceTypeStr != null && deviceTypeStr.equals("terminal")) {
					deviceType = DeviceType.TERMINAL;
					deviceKey = Protocol.getUserId(message);
					terminalMgr.addTerminal(deviceKey, out);
				} else {
					System.out.println("Not a node or terminal device so close the connection");
					socket.close();
					return;
				}

				while (true) {

					msgLength = Transport.getMessageLength(in);
					if (msgLength < 0) return;
					
					leftBytes = msgLength;
					readBytes = 0;
					while(leftBytes > 0) {
						readBytes = in.read(buffer, readBytes, leftBytes);
						if (readBytes < 0) return;
						leftBytes -= readBytes;
					}

					message = new String(buffer, 0, msgLength);
					System.out.println(">> " + message);
					//System.out.println(">> msgtype: " + Protocol.getMessageType(message));
					//System.out.println(">> value: " + Protocol.getSensorValue(message));
					
					String messageType = Protocol.getMessageType(message);
					if (messageType != null && messageType.equals("sensor")) {
						nodeMgr.handleNodeMsg(
								Protocol.getNodeId(message),
								Protocol.getSensorType(message),
								Protocol.getSensorValue(message));
					}

					if (messageType != null && messageType.equals("command")) {
						nodeMgr.sendCommandMsg(Protocol.getNodeId(message), message);
					}
				}

			} catch (IOException e) {
				System.out.println("Error handling client# " + clientNumber + ": " + e);
			} finally {
				try {
					socket.close();
					
					if (deviceType == DeviceType.NODE) {
						nodeMgr.removeNode(deviceKey);
					} else if (deviceType == DeviceType.TERMINAL) {
						terminalMgr.removeTerminal(deviceKey);
					}
				} catch (IOException e) {
					System.out.println("Couldn't close a socket, what's going on?");
				}
              System.out.println("Connection with client# " + clientNumber + " closed");
			}
		}
	}
}


