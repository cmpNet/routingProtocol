import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Date;
import java.util.Vector;

class DVRouter extends Router {
	class HostAndPort {
		public String hostAdd;
		public int hostRecvPort;
		public Date lastSendTime;
		public Date lastRecvTime;

		public HostAndPort(String hostAdd, int hostRecvPort, Date lastSendTime, Date lastRecvTime) {
			this.hostAdd = hostAdd;
			this.hostRecvPort = hostRecvPort;
			this.lastSendTime = lastSendTime;
			this.lastRecvTime = lastRecvTime;
		}
	}

	class RouterListElement {
		public String destAdd;
		public String nextHopAdd;
		public int hopNum;

		public RouterListElement(String destAdd, String nextHopAdd, int hopNum) {
			this.destAdd = destAdd;
			this.nextHopAdd = nextHopAdd;
			this.hopNum = hopNum;
		}
	}

	private static ConcurrentLinkedQueue<HostAndPort> directConnectHost;
	private static ConcurrentLinkedQueue<RouterListElement> routingTable;

	static void printDirectConnectHost() {
		for(HostAndPort x : directConnectHost) 
			System.out.println(x.hostAdd + ' ' + x.hostRecvPort);
	}

	static void printRoutingTable() {
		for(RouterListElement x : routingTable)
			System.out.println(x.destAdd + ' ' + x.nextHopAdd + ' ' + x.hopNum);
	}
	
	public DVRouter(int serverPort) {
		super(serverPort);
		directConnectHost = new ConcurrentLinkedQueue<HostAndPort>();
		routingTable = new ConcurrentLinkedQueue<RouterListElement>();
	}

	public void connect(String ip, int port) {
		send(ip, port, "SYN");
	}

	public void ackConnect(String ip, int port) {
		send(ip, port, "ACK");
	}

	void modifyHostAndPort(HostAndPort toAdd) {
		HostAndPort toModify = null;
		for(HostAndPort x : directConnectHost) {
			if(x.hostAdd.equals(toAdd.hostAdd)) {
				toModify = x;
				break;
			}
		}
		if(toModify != null) {
			directConnectHost.remove(toModify);
			if(toAdd.lastSendTime == null)
				toAdd.lastSendTime = toModify.lastSendTime;
			if(toAdd.lastRecvTime == null)
				toAdd.lastRecvTime = toModify.lastRecvTime;
		}
		directConnectHost.add(toAdd);
	}

	void addRouterListElement(RouterListElement toAdd) {
		RouterListElement toRemove = null;
		for(RouterListElement x : routingTable) {
			if(x.destAdd.equals(toAdd.destAdd)) {
				toRemove = x;
				break;
			}
		}
		if(toRemove != null)
			routingTable.remove(toRemove);
		routingTable.add(toAdd);
	}

	@Override
	protected void handleMessage(Message m) {
		String[] temps = m.message.split("!");
		modifyHostAndPort(new HostAndPort(m.address.getHostAddress(), m.port, null, new Date()));			
		addRouterListElement(new RouterListElement(m.address.getHostAddress(), m.address.getHostAddress(), 1));
		
		//manipulate ACK
		if(temps[0].equals("ACK")) {
			System.out.println("receive ACK from: " + m.address.getHostAddress());
			modifyHostAndPort(new HostAndPort(m.address.getHostAddress(), m.port, new Date(), null));			
			addRouterListElement(new RouterListElement(m.address.getHostAddress(), m.address.getHostAddress(), 1));
		}
		//manipulate SYN
		else if(temps[0].equals("SYN")) {
			System.out.println("receive SYN from: " + m.address.getHostAddress());			
			modifyHostAndPort(new HostAndPort(m.address.getHostAddress(), m.port, new Date(), null));			
			ackConnect(m.address.getHostAddress(), m.port);
			addRouterListElement(new RouterListElement(m.address.getHostAddress(), m.address.getHostAddress(), 1));
		}
		//manipulate routing table
		else if(temps[0].equals("table")) {
			HostAndPort toModify = null;
			for(HostAndPort x : directConnectHost) {
				if(x.hostAdd.equals(m.address.getHostAddress())) {
					toModify = x;
					break;
				}
			}

			int numOfEle = Integer.parseInt(temps[1]);
			for(int i = 0; i < numOfEle; i++) {
				String dest = temps[i * 2 + 2];
				int hop = Integer.parseInt(temps[i * 2 + 3]);

				RouterListElement toAdd = null;
				RouterListElement toRemove = null;
				boolean thisDestExist = false;
				for(RouterListElement x : routingTable) {
					if(dest.equals(x.destAdd)) {
						if(x.nextHopAdd.equals(m.address) || x.hopNum > 1 + hop) {
							toRemove = x;
							toAdd = new RouterListElement(x.destAdd, m.address.getHostAddress(), 1 + hop);
							break;
						}
						thisDestExist = true;
					}
				}
				if(toAdd != null) {
					routingTable.remove(toRemove);
					routingTable.add(toAdd);
				}
				else if(thisDestExist == false) {
					routingTable.add(new RouterListElement(dest, m.address.getHostAddress(), 1 + hop));
				}
				
			}
		}
		//manipulate message
		else if(temps[0].equals("message")) {
			if(temps[2].equals(localIp.getHostAddress())) {
				System.out.println("receive message from: " + m.address.getHostAddress() 
					                + "\nthis is a message to this host."
					                + "\nthe source host is: " + temps[1] 
					                + "\nthe message content is: " + temps[3]);
			}
			else {
				System.out.println("receive message from: " + m.address.getHostAddress() 
					                + "\nthis is a message to: " + temps[2]
					                + "\nthe source host is: " + temps[1] 
					                + "\nthe message content is: " + temps[3]);
				RouterListElement next = null;
				for(RouterListElement x : routingTable) {
					if(x.destAdd.equals(temps[2])) {
						next = x;
						break;
					}
				}
				if(next != null) {
					HostAndPort corr = null;
					for(HostAndPort y : directConnectHost) {
						if(y.hostAdd.equals(next.nextHopAdd)) {
							corr = y;
							break;
						}
					}
					if(corr != null) {
						System.out.println("route this message to: " + next.nextHopAdd);
						send(next.nextHopAdd, corr.hostRecvPort, m.message);
					}
					else
						System.out.println("routing error detected! ");
				}
				else {
					System.out.println("routing error detected! what the hell?");
				}
			}
		}
	}

	@Override
	protected void sendMessage() {
		HostAndPort toRemove = null;
		HostAndPort toModify = null;
		for(HostAndPort x : directConnectHost) {
			long isDown = new Date().getTime() - x.lastRecvTime.getTime();
			if(isDown > 15000) { //15 seconds
				toRemove = x;
				break;
			}
			
			long sendNew = 5001;
			if(x.lastSendTime != null)
				sendNew = new Date().getTime() - x.lastSendTime.getTime();
			if(sendNew > 5000) { //5 seconds
				String message = "table";
				String table = "";
				int tableSize = 0;
				for(RouterListElement y : routingTable) {
					if(!y.nextHopAdd.equals(x.hostAdd) && !y.destAdd.equals(x.hostAdd)) {
						table = table + '!' + y.destAdd + '!' + String.valueOf(y.hopNum);
						tableSize = tableSize + 1;
					}
				}
				message = message + '!' + String.valueOf(tableSize) + table;
				toModify = new HostAndPort(x.hostAdd, x.hostRecvPort, new Date(), null);
				send(x.hostAdd, x.hostRecvPort, message);
				break;
			}
		}		
		if(toRemove != null) {
			directConnectHost.remove(toRemove);
			Vector<RouterListElement> removeList = new Vector<RouterListElement>();
			for(RouterListElement x : routingTable) {
				if(x.destAdd.equals(toRemove.hostAdd) || x.nextHopAdd.equals(toRemove.hostAdd)) {
					removeList.add(x);
				}
			}
			for(RouterListElement x : removeList)
				routingTable.remove(x);
		}
		else if(toModify != null)
			modifyHostAndPort(toModify);
	}

	public static void main(String[] args) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Please input port");
			DVRouter router = new DVRouter(Integer.valueOf(reader.readLine()));
			System.out.println("Connect a router : -c ip port");
			System.out.println("Send a message : -s ip message");
			System.out.println("Print router table : -r");
			System.out.println("Print direct connect host : -d");
			String s = null;
			while ((s = reader.readLine()) != null) {
				String[] temps = s.split(" ");
				if (temps[0].equals("-c")) {
					router.connect(temps[1], Integer.valueOf(temps[2]));
				} else if (temps[0].equals("-s")) {
					String addr = null;
					int port = -1;
					for(RouterListElement x : routingTable) {
						if(x.destAdd.equals(temps[1])) {
							addr = x.nextHopAdd;
							for(HostAndPort y : directConnectHost) {
								if(addr.equals(y.hostAdd)) {
									port = y.hostRecvPort;
									break;
								}
							}
							break;
						}
					}
					if(addr != null && port != -1)
						router.send(addr, port, "message!" + localIp.getHostAddress() + '!' + temps[1] + '!' + temps[2]);
					else
						System.out.println("can not access this host!");
				}
				else if(temps[0].equals("-r")) {
					printRoutingTable();
				}
				else if(temps[0].equals("-d")) {
					printDirectConnectHost();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}