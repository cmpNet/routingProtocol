import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.*;

class Peer extends Router {
	// Controller 信息
	public String controllerIp;
	public int controllerPort = 2016;
	// 邻居表
	public List<Entry<Entry<String, Integer>, Entry<Integer, Integer>>> neighborTable = new ArrayList<>();
	// 待转发的数据包
	public Queue<String> packages = new LinkedList<String>();

	public Peer(int serverPort) {
		super(serverPort);
	}

	public void addToNeighborTable(String ip, int port) {
		Entry<String, Integer> neighbor = new AbstractMap.SimpleEntry<>(ip, port);
		Entry<Integer, Integer> reachable = new AbstractMap.SimpleEntry<>(1, 1);  // 第一位表示网络距离，第二位表示是否存活
		Entry<Entry<String, Integer>, Entry<Integer, Integer>> connect = new AbstractMap.SimpleEntry<>(neighbor, reachable);
		int flag = 1;
		for (int i = 0; i < neighborTable.size(); i++)
			if (neighborTable.get(i).equals(connect))
				flag = 0;
		if (flag == 1) {
			neighborTable.add(connect);
			System.out.println("[ INFO ] Connect to " + ip + ":" + port + " Successfully!");
		} else {
			System.out.println("[ INFO ] Already Connected to " + ip + ":" + port + "...");
		}
	}

	// 这个是用来处理收到的消息的
	@Override
	protected void handleMessage(Message m) {
		String[] temps = m.message.split("\\|");
		if (m.message.equals("|SYN|")) {
			sendMessage(controllerIp, controllerPort, "ADD|" + m.address.getHostAddress() + "|" + m.port);
			sendMessage(m.address.getHostAddress(), m.port, "|ACK|");
			addToNeighborTable(m.address.getHostAddress(), m.port);
		} else if (m.message.equals("|ACK|")) {
			addToNeighborTable(m.address.getHostAddress(), m.port);
		} else if (m.message.equals("|CSYN|")) {
			sendMessage(m.address.getHostAddress(), m.port, "|CACK|");
		} else if (m.message.equals("|CACK|")) {
			// 邻居依然存活
		} else if (temps[0].equals("RES")) {
			String message = packages.poll();
			if (temps.length == 3) {
			  sendMessage(temps[1], Integer.valueOf(temps[2]), message);
			} else {
				String[] routingInfo = message.split("\\*");
				String newMessage = routingInfo[0] + "*END*" + routingInfo[1] + "*" + routingInfo[2];
				sendMessage(temps[1], Integer.valueOf(temps[2]), newMessage);
			}
		} else if (temps[0].equals("DATA")) {
			String[] routingInfo = m.message.split("\\*");
			if (!routingInfo[1].equals("END")) {
				String newMessage = routingInfo[0] + "->" + m.address.getHostAddress() + ":" +
														m.port + "*" + routingInfo[1] + "*" + routingInfo[2];
				packages.offer(newMessage);
				String[] detailedInfo = routingInfo[1].split(":");
				sendMessage(controllerIp, controllerPort, "REQ|" + detailedInfo[0] + "|" + detailedInfo[1]);
			} else {
				String[] printInfo = temps[1].split("\\*");
				System.out.println("Routing: " + printInfo[0] + "->" + m.address.getHostAddress() +
				                   ":" + m.port);
				System.out.println("Message: " + printInfo[3]);
			}
		}
	}

	// 这个是用来跟其他路由建立连接的
	@Override
	public void connect(String ip, int port) {
		sendMessage(ip, port, "|SYN|");
	}

	// 这个是用来传消息的
	@Override
	public boolean sendMessage(String ip, int port, String message) {
		try {
		  send(ip, port, message);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public void sendToPeer(String ip, int port, String message) {
		sendMessage(controllerIp, controllerPort, "REQ|" + ip + "|" + port);
		packages.offer("DATA|*" + ip + ":" + port + "*" + message);
	}

	public static void main(String[] args) {
		try {
			System.out.print("\033\143");
			System.out.println("+--------------------------------+");
			System.out.println("|              PEER              |");
			System.out.println("+--------------------------------+");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			// 设置自己的端口
			System.out.println("[CONFIG] Input the running port:");
			Peer router = new Peer(Integer.valueOf(reader.readLine()));
			// 设置控制器 IP 和端口
			System.out.println("[CONFIG] Connect the controller: ip");
			router.controllerIp = reader.readLine();
			router.connect(router.controllerIp, router.controllerPort);
			// 检查邻居是否存活
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(new checkNeighbor(router), 0, 3000);
			// 主体
			System.out.println("[ HELP ] Connect a router: -c ip port");
			System.out.println("[ HELP ] Send a message: -s ip port message");
			String s = null;
			while ((s = reader.readLine()) != null) {
				String[] temps = s.split(" ");
				if (temps[0].equals("-c")) {
					router.connect(temps[1], Integer.valueOf(temps[2]));
				} else if (temps[0].equals("-s")) {
					router.sendToPeer(temps[1], Integer.valueOf(temps[2]), temps[3]);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

// 检查邻居是否存活
class checkNeighbor extends TimerTask {
  Peer router;
	checkNeighbor(Peer r) {
		router = r;
	}
  public void run() {
		try {
			for (int i = 0; i < router.neighborTable.size(); i++) {
				String ip = router.neighborTable.get(i).getKey().getKey();
				int port = router.neighborTable.get(i).getKey().getValue();
			  if (router.sendMessage(ip, port, "|CSYN|") == false) {
					if (!router.neighborTable.get(i).getValue().getValue().equals(-1))
					  System.out.println("[ INFO ] " + ip + ":" + port + " is down!");
					Entry<Integer, Integer> reachable = new AbstractMap.SimpleEntry<>(1, -1);
					router.neighborTable.get(i).setValue(reachable);
					router.sendMessage(router.controllerIp, router.controllerPort, "DOWN|" + ip + "|" + port);
					continue;
				}
        if (router.neighborTable.get(i).getValue().getValue().equals(-1)) {
				  System.out.println("[ INFO ] " + ip + ":" + port + " is back online!");
					router.sendMessage(router.controllerIp, router.controllerPort, "UP|" + ip + "|" + port);
				}
				Entry<Integer, Integer> reachable = new AbstractMap.SimpleEntry<>(1, 1);
				router.neighborTable.get(i).setValue(reachable);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
  }
}
