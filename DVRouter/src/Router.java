import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Router {
	class Message {
		public InetAddress address;
		public int port;
		public String message;

		public Message(InetAddress address, String message) {
			this.address = address;
			String[] temps = message.split("!");
			this.port = Integer.valueOf(temps[0]);
			this.message = "";
			for (int i = 1; i < temps.length; i++) {
				this.message += temps[i];
				if(i != temps.length - 1)
					this.message += "!";
			}
		}
	}

	class HostAndPort {
		public InetAddress hostAdd;
		public int hostRecvPort;

		public HostAndPort(InetAddress hostAdd, int hostRecvPort) {
			this.hostAdd = hostAdd;
			this.hostRecvPort = hostRecvPort;
		}
	}

	class RouterListElement {
		public InetAddress destAdd;
		public InetAddress nextHopAdd;
		public int hopNum;

		public RouterListElement(InetAddress destAdd, InetAddress nextHopAdd, int hopNum) {
			this.destAdd = destAdd;
			this.nextHopAdd = nextHopAdd;
			this.hopNum = hopNum;
		}
	}

	protected ConcurrentLinkedQueue<Message> messageBuffer;	
	protected static int serverPort;
	protected static InetAddress localIp;

	public Router(int serverPort) {
		getLocalIp();
		System.out.println("the ip of this host: " + localIp.getHostAddress());
		this.serverPort = serverPort;
		messageBuffer = new ConcurrentLinkedQueue<Message>();
		ServerThread serverThread = new ServerThread(serverPort);
		new Thread(serverThread).start();
		RouterAction routerAction = new RouterAction();
		new Thread(routerAction).start();
	}

	class ServerThread implements Runnable {
		private int serverPort;
		private ServerSocket server;

		public ServerThread(int serverPort) {
			this.serverPort = serverPort;
		}

		public void run() {
			try {
				server = new ServerSocket(serverPort);
				while (true) {
					Socket socket = server.accept();
					ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
					messageBuffer.add(new Message(socket.getInetAddress(), (String)in.readObject()));
					in.close();
					socket.close();

				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	protected void handleMessage(Message m) {} //override it
	protected void sendMessage() {} //override it
	
	class RouterAction implements Runnable {
		public void run() {
			while (true) {
				if (!messageBuffer.isEmpty()) {
					handleMessage(messageBuffer.poll());
					// try {
					// 	Thread.sleep(10);
					// } catch (Exception e) {
					// 	e.printStackTrace();
					// }
				}
				sendMessage();
			}
		}
	}

	protected boolean send(String ip, int port, String message) {
		try {
			Socket socket = new Socket(ip, port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(serverPort + "!" + message);
			out.flush();
			out.close();
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private static void getLocalIp() {
        try {
            localIp = InetAddress.getLocalHost();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
