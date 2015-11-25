//prject3 xiangxiao dongcai
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.lang.*;
import java.util.concurrent.Semaphore;


public class Project3{
	//field of the Project3
	public static String configFile;
	public static int lineCount; //record how many effective lines in config file
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int tobSendDelay;
	public static ArrayList<String> nodeNames = new ArrayList<String>();
	public static ArrayList<String> hostNames = new ArrayList<String>();
	public static ArrayList<String> portNums = new ArrayList<String>();
	public static PrintWriter writerOutputFile;

	public static String nodeID;
	public static ServerSocket serverSock;


	public static void main(String[] args){
		if(args.length < 2 || args.length > 3)
		{
			System.out.println("Please input the node ID and config file");
			return;
		}
		nodeID = args[0];
		configFile = args[1];
		readConfig();
		initiateOutputFile();
		enableServer();
		System.out.println("Node "+ nodeID + " System time: " + System.currentTimeMillis());

		//Thread mutex = new Thread(new MutexWorker(nodeID, configFile, serverSock));
		//mutex.start();
		//MutexWorker mutex = new MutexWorker(nodeID, configFile, serverSock); // this is called in tob worker
		TobWorker tob = new TobWorker(nodeID, configFile, serverSock);
		
		System.out.println("end of main()");
	}

	static void initiateOutputFile(){
		try{
			writerOutputFile = new PrintWriter(configFile.replace(".txt", "") + "-" + nodeID +".out");
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	static void readConfig(){
		lineCount = 0;
		//System.out.println("Node "+ nodeID + ": Starting to read config file!");
		try(BufferedReader br = new BufferedReader(new FileReader(configFile))){
			String currentLine;
			while ((currentLine = br.readLine()) != null){
				if(currentLine.trim().length() == 0) continue;
				if(currentLine.trim().charAt(0) == '#') continue;
				if(currentLine.trim().charAt(0) != '#' && currentLine.trim().contains("#")){
					currentLine = currentLine.substring(0, currentLine.indexOf('#')); 
				}
				lineCount++;
				currentLine = currentLine.trim().replaceAll("\\s+", " ");
				//Section 1 : six parameters
				if(lineCount == 1 && !currentLine.contains("dc")){
					//System.out.println("Section 1: ");
					//System.out.println("Reading 3 parameters for node " + nodeID);
					String[] parts1 = currentLine.split("\\s+");
					if(parts1.length != 3){
						System.out.println("Error config information in line 1 for node " + nodeID);
						return;
					}else{
						numNodes = Integer.parseInt(parts1[0]);
						numMessages = Integer.parseInt(parts1[1]);
						tobSendDelay = Integer.parseInt(parts1[2]);
						//System.out.println("3 parameters for node " + nodeID + " : ");
						//System.out.println(numNodes+" "+numMessages+" "+tobSendDelay);
						continue;
					}
				}
				//Section 2: listen ports
				if(lineCount > 1 && lineCount <= numNodes + 1 && currentLine.contains("dc")){
					String[] parts2 = currentLine.split("\\s+");
					nodeNames.add(parts2[0]);
					hostNames.add(parts2[1]);
					portNums.add(parts2[2]);
					System.out.println("Section 2: ");
					System.out.println("Node: " + parts2[0] + " host: " + parts2[1] + " port: " + parts2[2]);
					//System.out.println("lineCount: " + lineCount);
					continue;
				}
				System.out.println("Bad config file with excessive paths or other incorrect information");
			}
		}catch(IOException e){
			System.out.println("readConfig() exceptions ");
			e.printStackTrace();
		}
	}

	static void enableServer(){
		int port = 0;
		try{
			for(int i=0; i<nodeNames.size(); i++){
				if(Integer.valueOf(nodeID) == Integer.valueOf(nodeNames.get(i))){
					port = Integer.parseInt(portNums.get(i));
				}else{
					continue;
				}
			}
			serverSock = new ServerSocket(port);
			System.out.println("Node " + nodeID + " listening on port " + port);
		}catch (IOException e){
			System.out.println("Could not listen on port " + port);
			System.exit(-1);
		}
	}

	static void sleep(int milliseconds){
		try {
			Thread.sleep(milliseconds);
		}catch(InterruptedException ex){
			Thread.currentThread().interrupt();
		}
	}

}

interface TobInterface{
	public void tobSend(String message);
	public void tobReceive();
}
interface MutexInterface{
	public void csEnter();
	public void csExit();
}

class TobWorker implements Runnable, TobInterface{
	//suport tobSend(), tobReceive()
	//field of class TobWorker
	public static String configFileTob;
	public static String idTob;
	public static ServerSocket tobServerSocket;
	public static Socket[] tobOutSockets;

	public static int lineCount;
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int tobSendDelay;
	public static ArrayList<String> nodeNames = new ArrayList<String>();
	public static ArrayList<String> hostNames = new ArrayList<String>();
	public static ArrayList<String> portNums = new ArrayList<String>();

	//Constructor
	TobWorker(String id, String config, ServerSocket server){
		idTob = id;
		configFileTob = config;
		tobServerSocket = server;
		System.out.println("Node " + idTob + " initiating tob service");
		readConfigTob();
		connectAllNodes();
		MutexWorker mutex = new MutexWorker(idTob, configFileTob, tobServerSocket); // this is called in tob worker
		Thread listen = new Thread(new listenThread());
		listen.start();


	}

	class listenThread implements Runnable{
		//constructor
		listenThread(){}
		public void run(){
			listenSocket();
		}
	}

	public void listenSocket(){
		boolean scanning = true;
		while(scanning){
			ClientWorker w;
			try{
				w = new ClientWorker(tobServerSocket.accept());
				Thread t = new Thread(w);
				t.start();
			}catch(IOException e){
				System.out.println("Accept failed in listenSocket() in MutexWorker class, node "+idTob+" terminated");
				try {
					tobServerSocket.close();
					Thread.currentThread().interrupt();
					}catch(IOException ex){
						System.out.println("This node has already terminated, nodeID: " + idTob);
					}
				System.exit(-1);
			}
		}
	}

	class ClientWorker implements Runnable{
		private Socket client;
		private volatile boolean scanning = true;

		//Constructor
		ClientWorker(Socket client) {
			this.client = client;
		}
		
		public void run(){
			String line;
			BufferedReader in = null;
			PrintWriter out = null;
			//boolean scanning = true;
			int intNodeID = Integer.parseInt(idTob);
			try{
				in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				out = new PrintWriter(client.getOutputStream(), true);
			}catch (IOException e) {
				System.out.println("in or out failed in run()");
				System.exit(-1);
			}
			while(scanning){//receive events need to handle semaphore too
				try{
					line = in.readLine();
					if(line != null){
						System.out.println("Node "+idTob+" message received in tob service listen socket: " + line);	
					}
				}catch(IOException e){
					System.out.println("Read failed from ClientWorker-->run()--> while(scanning)-->try{}");
					scanning = false;
					//System.exit(-1);
				}
			}	
		}
	}

	public static void readConfigTob(){
		lineCount = 0;
		System.out.println("Node "+ idTob + ": Starting to read config file in tob service!");
		try(BufferedReader br = new BufferedReader(new FileReader(configFileTob))){
			String currentLine;
			while ((currentLine = br.readLine()) != null){
				if(currentLine.trim().length() == 0) continue;
				if(currentLine.trim().charAt(0) == '#') continue;
				if(currentLine.trim().charAt(0) != '#' && currentLine.trim().contains("#")){
					currentLine = currentLine.substring(0, currentLine.indexOf('#')); 
				}
				lineCount++;
				currentLine = currentLine.trim().replaceAll("\\s+", " ");
				//Section 1 : 3 parameters
				if(lineCount == 1 && !currentLine.contains("dc")){
					//System.out.println("Section 1: ");
					//System.out.println("Reading 3 parameters for node " + nodeID);
					String[] parts1 = currentLine.split("\\s+");
					if(parts1.length != 3){
						System.out.println("Error config information in line 1 for node " + idTob);
						return;
					}else{
						numNodes = Integer.parseInt(parts1[0]);
						numMessages = Integer.parseInt(parts1[1]);
						tobSendDelay = Integer.parseInt(parts1[2]);
						//System.out.println("3 parameters for node " + nodeID + " : ");
						//System.out.println(numNodes+" "+numMessages+" "+tobSendDelay);
						continue;
					}
				}
				//Section 2: listen ports
				if(lineCount > 1 && lineCount <= numNodes + 1 && currentLine.contains("dc")){
					String[] parts2 = currentLine.split("\\s+");
					nodeNames.add(parts2[0]);
					hostNames.add(parts2[1]);
					portNums.add(parts2[2]);
					//System.out.println("Section 2: ");
					//System.out.println("Node: " + parts2[0] + " host: " + parts2[1] + " port: " + parts2[2]);
					//System.out.println("lineCount: " + lineCount);
					continue;
				}
				System.out.println("Bad config file with excessive paths or other incorrect information");
			}
		}catch(IOException e){
			System.out.println("readConfigTob() exceptions ");
			e.printStackTrace();
		}
	}

	public void run(){

	}
	public void tobSend(String message){
		//mutex.csEnter();
		//broadcast the message
		//mutex.csExit();

	}
	public void tobReceive(){

	}

	public static void sleep(int milliseconds){
		try {
			Thread.sleep(milliseconds);
		}catch(InterruptedException ex){
			Thread.currentThread().interrupt();
		}
	}

	public static void connectAllNodes(){
		tobOutSockets = new Socket[numNodes];
		int intID = Integer.parseInt(idTob);
		for(int i=0; i<numNodes; i++){
			int target = Integer.parseInt(nodeNames.get(i));
			String host = hostNames.get(target)+ ".utdallas.edu";
			int port = Integer.parseInt(portNums.get(target));
			tryConnect(host, port, target);
		}
		return;
	}

	public static void tryConnect(String host, int port, int target){
		boolean scanning = true;
		int times = 0;
		while(scanning){
			try{
				System.out.println("host and port and target: ");
				System.out.println(host + " " + port + " " + target);
				tobOutSockets[target] = new Socket(host, port);
				scanning = false;
				PrintWriter writer = new PrintWriter(tobOutSockets[target].getOutputStream(), true); 	//boolean autoflush or not?
				writer.println("Hello, I am node "+ idTob + " connecting in tob service");
				//writer.close();
			}catch(IOException ex){
				if(times > 100){
					System.out.println("Connection failed, need to fix some bugs, giving up reconnecting");
					scanning = false;
				}
				System.out.println("Connection failed, reconnecting in 0.5 seconds");
				//ex.printStackTrace();
				times++;
				sleep(500);
			}
		}
	}

}

class MutexWorker implements Runnable, MutexInterface{
	//field:
	public static String configFileMutex;
	public static String idMutex;
	public static int lineCount;
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int tobSendDelay;
	public static ArrayList<String> nodeNames = new ArrayList<String>();
	public static ArrayList<String> hostNames = new ArrayList<String>();
	public static ArrayList<String> portNums = new ArrayList<String>();
	public static Socket[] mutexOutSockets;
	public static ServerSocket mutexServerSocket;
	//Constructor
	MutexWorker(String id, String config, ServerSocket server){
		idMutex = id;
		configFileMutex = config;
		mutexServerSocket = server;
		System.out.println("Node " + idMutex + " initiating mutex service");
		readConfigMutex();
		connectAllNodes();
		Thread listen = new Thread(new listenThread());
		listen.start();
		
	}

	class listenThread implements Runnable{
		//constructor
		listenThread(){}
		public void run(){
			listenSocket();
		}
	}

	public void listenSocket(){
		boolean scanning = true;
		while(scanning){
			ClientWorker w;
			try{
				w = new ClientWorker(mutexServerSocket.accept());
				Thread t = new Thread(w);
				t.start();
			}catch(IOException e){
				System.out.println("Accept failed in listenSocket() in MutexWorker class, node "+idMutex+" terminated");
				try {
					mutexServerSocket.close();
					Thread.currentThread().interrupt();
					}catch(IOException ex){
						System.out.println("This node has already terminated, nodeID: " + idMutex);
					}
				System.exit(-1);
			}
		}
	}

	class ClientWorker implements Runnable{
		private Socket client;
		private volatile boolean scanning = true;

		//Constructor
		ClientWorker(Socket client) {
			this.client = client;
		}
		
		public void run(){
			String line;
			BufferedReader in = null;
			PrintWriter out = null;
			//boolean scanning = true;
			int intNodeID = Integer.parseInt(idMutex);
			try{
				in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				out = new PrintWriter(client.getOutputStream(), true);
			}catch (IOException e) {
				System.out.println("in or out failed in run()");
				System.exit(-1);
			}
			while(scanning){//receive events need to handle semaphore too
				try{
					line = in.readLine();
					if(line != null){
						System.out.println("Node "+idMutex+" message received in mutex service listen socket: " + line);	
					}
				}catch(IOException e){
					System.out.println("Read failed from ClientWorker-->run()--> while(scanning)-->try{}");
					scanning = false;
					//System.exit(-1);
				}
			}	
		}
	}
	
	public void run(){
		System.out.println("Hello this is class: MutexWorker.run(), Node: " + idMutex);
	}

	public void csEnter(){
		//this is how a node initiates a cs request
		//returns when it has the permission to enter cs
	}

	public void csExit(){
		//this is how a node inform the service that it has finished broadcasting and exit cs

	}

	public static void sleep(int milliseconds){
		try {
			Thread.sleep(milliseconds);
		}catch(InterruptedException ex){
			Thread.currentThread().interrupt();
		}
	}

	public static void readConfigMutex(){
		lineCount = 0;
		System.out.println("Node "+ idMutex + ": Starting to read config file in mutex service!");
		try(BufferedReader br = new BufferedReader(new FileReader(configFileMutex))){
			String currentLine;
			while ((currentLine = br.readLine()) != null){
				if(currentLine.trim().length() == 0) continue;
				if(currentLine.trim().charAt(0) == '#') continue;
				if(currentLine.trim().charAt(0) != '#' && currentLine.trim().contains("#")){
					currentLine = currentLine.substring(0, currentLine.indexOf('#')); 
				}
				lineCount++;
				currentLine = currentLine.trim().replaceAll("\\s+", " ");
				//Section 1 : 3 parameters
				if(lineCount == 1 && !currentLine.contains("dc")){
					//System.out.println("Section 1: ");
					//System.out.println("Reading 3 parameters for node " + nodeID);
					String[] parts1 = currentLine.split("\\s+");
					if(parts1.length != 3){
						System.out.println("Error config information in line 1 for node " + idMutex);
						return;
					}else{
						numNodes = Integer.parseInt(parts1[0]);
						numMessages = Integer.parseInt(parts1[1]);
						tobSendDelay = Integer.parseInt(parts1[2]);
						//System.out.println("3 parameters for node " + nodeID + " : ");
						//System.out.println(numNodes+" "+numMessages+" "+tobSendDelay);
						continue;
					}
				}
				//Section 2: listen ports
				if(lineCount > 1 && lineCount <= numNodes + 1 && currentLine.contains("dc")){
					String[] parts2 = currentLine.split("\\s+");
					nodeNames.add(parts2[0]);
					hostNames.add(parts2[1]);
					portNums.add(parts2[2]);
					//System.out.println("Section 2: ");
					//System.out.println("Node: " + parts2[0] + " host: " + parts2[1] + " port: " + parts2[2]);
					//System.out.println("lineCount: " + lineCount);
					continue;
				}
				System.out.println("Bad config file with excessive paths or other incorrect information");
			}
		}catch(IOException e){
			System.out.println("readConfigMutex() exceptions ");
			e.printStackTrace();
		}
	}

	public static void connectAllNodes(){
		mutexOutSockets = new Socket[numNodes];
		int intID = Integer.parseInt(idMutex);
		for(int i=0; i<numNodes; i++){
			int target = Integer.parseInt(nodeNames.get(i));
			String host = hostNames.get(target)+ ".utdallas.edu";
			int port = Integer.parseInt(portNums.get(target));
			tryConnect(host, port, target);
		}
		return;
	}

	public static void tryConnect(String host, int port, int target){
		boolean scanning = true;
		int times = 0;
		while(scanning){
			try{
				System.out.println("host and port and target: ");
				System.out.println(host + " " + port + " " + target);
				mutexOutSockets[target] = new Socket(host, port);
				scanning = false;
				PrintWriter writer = new PrintWriter(mutexOutSockets[target].getOutputStream(), true); 	//boolean autoflush or not?
				writer.println("Hello, I am node "+ idMutex + " connecting in mutex service");
				//writer.close();
			}catch(IOException ex){
				if(times > 100){
					System.out.println("Connection failed, need to fix some bugs, giving up reconnecting");
					scanning = false;
				}
				System.out.println("Connection failed, reconnecting in 0.5 seconds");
				//ex.printStackTrace();
				times++;
				sleep(500);
			}
		}
	}

}
