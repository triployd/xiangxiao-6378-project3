//prject3 xiangxiao dongcai
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.lang.*;


public class Project3{
	//field of the Project2
	public static String configFile;
	public static int lineCount; //record how many effective lines in config file
	public static int numNodes; //record the number of nodes in the system
	public static int numMessages;
	public static int tobSendDelay;
	public static ArrayList<String> nodeNames = new ArrayList<String>();
	public static ArrayList<String> hostNames = new ArrayList<String>();
	public static ArrayList<String> portNums = new ArrayList<String>();
	
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
		
	}

	static void readConfig(){
		lineCount = 0;
		System.out.println("Node "+ nodeID + ": Starting to read config file!");
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
					System.out.println("Section 1: ");
					System.out.println("Reading 3 parameters for node " + nodeID);
					String[] parts1 = currentLine.split("\\s+");
					if(parts1.length != 3){
						System.out.println("Error config information in line 1 for node " + nodeID);
						return;
					}else{
						numNodes = Integer.parseInt(parts1[0]);
						numMessages = Integer.parseInt(parts1[1]);
						tobSendDelay = Integer.parseInt(parts1[2]);
						System.out.println("3 parameters for node " + nodeID + " : ");
						System.out.println(numNodes+" "+numMessages+" "+tobSendDelay);
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
					System.out.println("lineCount: " + lineCount);
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