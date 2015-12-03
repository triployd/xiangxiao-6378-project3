//prject3 xiangxiao dongcai
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.lang.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Project3{
	//field of the Project3
	public static String configFile;
	public static int lineCount; //record how many effective lines in config file
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int tobSendDelay;
	public static volatile int numSent = 0;
	public static volatile int receivedNum = 0;
	public static long lastTimeSent = 0;
	public static long currentTime = 0;
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
		
		enableServer();
		System.out.println("Node "+ nodeID + " System time: " + System.currentTimeMillis());
		sleep(5000);

		
		//TobWorker tob = new TobWorker(nodeID, configFile, serverSock);

		Listen listen = new Listen (nodeID, configFile, serverSock);
		
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
	public String tobReceive();
}
interface MutexInterface{
	public void csEnter(String tag);
	public void csExit(String tag);
}

class Listen{

	public static String configFileListen;
	public static String idListen;
	public static ServerSocket listenServerSocket;
	public static Socket[] listenOutSockets;

	public static int lineCount;
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int listenSendDelay;
	public static volatile int numSent = 0;
	public static volatile int receivedNum = 0;
	public static long lastTimeSent = 0;
	public static long currentTime = 0;
	public static PrintWriter writerOutputFile;
	public static ArrayList<String> nodeNames = new ArrayList<String>();
	public static ArrayList<String> hostNames = new ArrayList<String>();
	public static ArrayList<String> portNums = new ArrayList<String>();

	public static TobWorker tob;
	public static MutexWorker mutex;

	Listen(String id, String config, ServerSocket server){
		idListen = id;
		configFileListen = config;
		listenServerSocket = server;
		readConfigListen();
		connectAllNodes();
		initiateOutputFile();
		Thread listen = new Thread(new ListenThread());
		listen.start();

		mutex = new MutexWorker(idListen, configFileListen);
		tob = new TobWorker(idListen, configFileListen, mutex);

		Thread send = new Thread(new SendThread(tob));
		send.start();
		Thread receive = new Thread(new ReceiveThread(tob));
		receive.start();

	}

	static void initiateOutputFile(){
		try{
			writerOutputFile = new PrintWriter(configFileListen.replace(".txt", "") + "-" + idListen +".out");
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	class SendThread implements Runnable{
		TobWorker tob;
		SendThread(TobWorker tob1){
			this.tob = tob1;
		}
		public void run(){
			while(true){
				Random random = new Random();
				while(numSent < numMessages){
					currentTime = System.currentTimeMillis();
					if(currentTime - lastTimeSent >= listenSendDelay){
						tob.tobSend("BROADCAST "+Integer.toString(random.nextInt(10000)));
						numSent++;
						lastTimeSent = currentTime;
					}
					sleep(100);
				}
			}
		}
	}

	class ReceiveThread implements Runnable{
		TobWorker tob;
		public volatile boolean running = true;
		ReceiveThread(TobWorker tob1){
			this.tob = tob1;
		}
		public void run(){
			while(running){
				String receivedMsg = tob.tobReceive();
				writerOutputFile.println(receivedMsg);
				receivedNum++;
				System.out.println("receivedNum, numMessages: " + receivedNum + ", " + numMessages);
				if(receivedNum >= numMessages){
					System.out.println("Mark before writerOutputFile.close()");
					writerOutputFile.close();
					running = false;
				}
				sleep(100);
			}
		}
	}

	class ListenThread implements Runnable{
		//constructor
		ListenThread(){}
		public void run(){
			listenSocket();
		}
	}

	public void listenSocket(){
		boolean scanning = true;
		while(scanning){
			ClientWorker w;
			try{
				w = new ClientWorker(listenServerSocket.accept());
				Thread t = new Thread(w);
				t.start();
			}catch(IOException e){
				System.out.println("Accept failed in listenSocket() in Listen class, node "+idListen+" terminated");
				try {
					listenServerSocket.close();
					Thread.currentThread().interrupt();
					}catch(IOException ex){
						System.out.println("This node has already terminated, nodeID: " + idListen);
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
			int intNodeID = Integer.parseInt(idListen);
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
						System.out.println("Node "+idListen+" message received in Listen ClientWorker: " + line);
						if(line.contains("BROADCAST")){
							tob.receiveFromListen(line);
						}else if(line.contains("REQUEST")){
							mutex.receiveFromListen(line);
						}else if(line.contains("REPLY")){
							mutex.receiveFromListen(line);
						}else if(line.contains("RELEASE")){
							mutex.receiveFromListen(line);
						}
					}
				}catch(IOException e){
					System.out.println("Read failed from ClientWorker-->run()--> while(scanning)-->try{}");
					scanning = false;
					//System.exit(-1);
				}
			}
		}
	}

	public static void sleep(int milliseconds){
		try {
			Thread.sleep(milliseconds);
		}catch(InterruptedException ex){
			Thread.currentThread().interrupt();
		}
	}

	public static void readConfigListen(){
		lineCount = 0;
		System.out.println("Node "+ idListen + ": Starting to read config file in listen service!");
		try(BufferedReader br = new BufferedReader(new FileReader(configFileListen))){
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
						System.out.println("Error config information in line 1 for node " + idListen);
						return;
					}else{
						numNodes = Integer.parseInt(parts1[0]);
						numMessages = Integer.parseInt(parts1[1]);
						listenSendDelay = Integer.parseInt(parts1[2]);
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
			System.out.println("readConfigListen() exceptions ");
			e.printStackTrace();
		}
	}

	public static void connectAllNodes(){
		listenOutSockets = new Socket[numNodes];
		int intID = Integer.parseInt(idListen);
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
				listenOutSockets[target] = new Socket(host, port);
				scanning = false;
				PrintWriter writer = new PrintWriter(listenOutSockets[target].getOutputStream(), true); 	//boolean autoflush or not?
				writer.println("Hello, I am node "+ idListen + " connecting in listen service");
				//writer.close();
			}catch(IOException ex){
				if(times > 1000){
					System.out.println("Connection failed, need to fix some bugs, giving up reconnecting");
					scanning = false;
				}
				System.out.println("Connection failed in Listen, reconnecting in 0.5 seconds");
				ex.printStackTrace();
				times++;
				sleep(500);
			}
		}
	}


}

class TobWorker implements TobInterface{
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

	public static Lock lockReceive = new ReentrantLock();
	public static Lock lockSend = new ReentrantLock();

	public volatile Queue<String> receivedQueue = new LinkedList<String>(); //may be make this queue volatile
	public volatile Queue<String> sendQueue = new LinkedList<String>();
	public MutexWorker mutex;
	//Constructor
	TobWorker(String id, String config, MutexWorker mut){
		idTob = id;
		configFileTob = config;
		//tobServerSocket = server;
		mutex = mut;
		System.out.println("Node " + idTob + " initiating tob service");
		readConfigTob();
		connectAllNodes();
		
		//Thread listen = new Thread(new ListenThread());
		//listen.start();
		Thread send = new Thread(new SendThreadTob());
		send.start();

	}

	public void receiveFromListen(String message){
		if(message.contains("BROADCAST")){
			String[] parts = message.trim().split("\\s+");
			String randomReceived = parts[1];
			lockReceive.lock();
			try{
				receivedQueue.add(randomReceived);
			}finally{
				lockReceive.unlock();
			}
		}
	}

	public void broadcast(String message){
		for(int i=0; i<numNodes; i++){
			int target = Integer.parseInt(nodeNames.get(i));
			String host = hostNames.get(target) + ".utdallas.edu";
			int port = Integer.parseInt(portNums.get(target));
			try{
				PrintWriter writer = new PrintWriter(tobOutSockets[target].getOutputStream(), true);
				writer.println(message);
			}catch(IOException ex){
				System.out.println("Error in tob.broadcast(), unable to send the message, Node "+idTob);
				ex.printStackTrace();
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

	public void tobSend(String message){
		//just put the message into sendQueue is ok...
		lockSend.lock();
		try{
			sendQueue.add(message);
		}finally{
			lockSend.unlock();
		}
	}

	class SendThreadTob implements Runnable{
		SendThreadTob(){}
		public void run(){
			while(true){
				if(!sendQueue.isEmpty()){
					String message = sendQueue.remove();
					String tag = idTob + " " + Long.toString(System.currentTimeMillis());
					mutex.csEnter(tag);
					broadcast(message);
					System.out.println("Mark before mutex.csExit() in SendThreadTob");
					mutex.csExit(tag);
				}
				sleep(100);
			}
		}
	}

	public String tobReceive(){
		while(true){
			if(!receivedQueue.isEmpty()){
				//pass the number to app level
				return receivedQueue.remove();
			}else{
				sleep(100);
			}
		}
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
				if(times > 1000){
					System.out.println("Connection failed, need to fix some bugs, giving up reconnecting");
					scanning = false;
				}
				System.out.println("Connection failed in TOB, reconnecting in 0.5 seconds");
				//ex.printStackTrace();
				times++;
				sleep(500);
			}
		}
	}

}

class MutexWorker implements MutexInterface{
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
	public static volatile PriorityQueue<String> pQueue;
	public static Lock lockQueue = new ReentrantLock();
	public static Lock lockMap = new ReentrantLock();
	public static volatile HashMap<String, Integer> mapReply = new HashMap<String, Integer>();
	//Constructor
	MutexWorker(String id, String config ){
		idMutex = id;
		configFileMutex = config;
		//mutexServerSocket = server;
		System.out.println("Node " + idMutex + " initiating mutex service");
		pQueue = new PriorityQueue<String>(1000, new TimeStampComparator());
		readConfigMutex();
		//enableServerMutex();
		connectAllNodes();
		//Thread listen = new Thread(new listenThread());
		//listen.start();
		
	}

	public void receiveFromListen(String message){
		if(message.contains("REQUEST")){
			String s = message.substring(message.indexOf(" ")+1);
			System.out.println("REQUEST node and timeStamp :" + s);
			lockQueue.lock();
			try{
				pQueue.add(s);
				sendReply(s);
			}finally{
				lockQueue.unlock();
			}
		}else if(message.contains("REPLY")){
			//TODO: 1. extract the tag,
			//2. check if the tag has been received num%numNodes == 0 times
			String tag = message.substring(message.indexOf(" ")+1);
			System.out.println("REPLY node and timeStamp :" + tag);

			lockMap.lock();
			try{
				if(mapReply.containsKey(tag)){
					mapReply.put(tag, mapReply.get(tag)+1);
				}else{
					mapReply.put(tag, 1);
				}
			}finally{
				lockMap.unlock();
			}

		}else if(message.contains("RELEASE")){
			//TODO : remove pQueue's head, if the head contains the tag
			//question : what if the head does not contain the tag?
			if(!message.contains(pQueue.peek())){
				System.out.println("release is not on the head of the queue");
			}
			String remove = pQueue.poll();
			System.out.println("Node " + idMutex + " remove PriorityQueue entry "+ remove);
		}
	}

	class TimeStampComparator implements Comparator<String>{
		@Override
		public int compare(String x, String y){
			String node1 = x.substring(0, x.indexOf(" "));
			String node2 = y.substring(0, y.indexOf(" "));
			String t1 = x.substring(x.indexOf(" ")+1);
			String t2 = y.substring(y.indexOf(" ")+1);
			if(Long.parseLong(t1) == Long.parseLong(t2)){
				return Integer.parseInt(node1) - Integer.parseInt(node2);
			}else{
				return Long.parseLong(t1) > Long.parseLong(t2)? -1 : 1 ;
			}
		}
	}


	public void sendReply(String s){
		int target = Integer.parseInt(s.substring(0, s.indexOf(" ")));
		String host = hostNames.get(target) + ".utdallas.edu";
		int port = Integer.parseInt(portNums.get(target));
		try{
			PrintWriter writer = new PrintWriter(mutexOutSockets[target].getOutputStream(), true);
			writer.println("REPLY " + s);
		}catch(IOException ex){
			System.out.println("Error in mutex.sendReply(), unable to send the message, Node "+idMutex);
			ex.printStackTrace();
		}
	}

	public void broadcast(String message){
		for(int i=0; i<numNodes; i++){
			int target = Integer.parseInt(nodeNames.get(i));
			String host = hostNames.get(target) + ".utdallas.edu";
			int port = Integer.parseInt(portNums.get(target));
			try{
				PrintWriter writer = new PrintWriter(mutexOutSockets[target].getOutputStream(), true);
				writer.println(message);
			}catch(IOException ex){
				System.out.println("Error in mutex.broadcast(), unable to send the message, Node "+idMutex);
				ex.printStackTrace();
			}
		}
	}

	public void csEnter(String tag){
		//this is how a node initiates a cs request
		//returns when it has the permission to enter cs
		//TODO: 1. send request to other nodes
		//TODO: 2. while(true) return
		
		String msgSend = "REQUEST " + tag;
		broadcast(msgSend);
		while(true){
			if(mapReply.containsKey(tag)){
				System.out.println("Mark in csEnter(tag) 1, numNodes: "+ numNodes + " mapReply.get(tag): " + mapReply.get(tag));
				if(mapReply.get(tag) >= numNodes){
					System.out.println("Mark in csEnter(tag) 2");
					//System.out.println("pQueue.peek():" + pQueue.peek());
					if(tag.contains(pQueue.peek())){
						System.out.println("Returning from csEnter()");
						return;
					}
				}
			}
			sleep(100);
		}
	}

	public void csExit(String tag){
		//this is how a node inform the service that it has finished broadcasting and exit cs
		//TODO: 1. send release to other nodes
		broadcast("RELEASE " + tag);
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
					System.out.println("Mutex Section 2: ");
					System.out.println("Node: " + parts2[0] + " host: " + parts2[1] + " port: " + parts2[2]);
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
				if(times > 1000){
					System.out.println("Connection failed, need to fix some bugs, giving up reconnecting");
					scanning = false;
				}
				System.out.println("Connection failed in mutex, reconnecting in 0.5 seconds");
				//ex.printStackTrace();
				times++;
				sleep(500);
			}
		}
	}

}
