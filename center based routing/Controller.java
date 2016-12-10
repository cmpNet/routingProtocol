import java.io.*;
import java.util.*;
import java.util.Map.*;

class Controller extends Router {
	// 用于存放路由表
	public List<Entry<Entry<Entry<String, Integer>, Entry<String, Integer>>, Integer>> routingTable = new ArrayList<>();

  // 构造函数
	public Controller(int serverPort) {
		super(serverPort);
	}

	// 这个是用来处理收到的消息的
	@Override
	protected void handleMessage(Message m) {
		String[] temps = m.message.split("\\|");
		if (temps[0].equals("ADD")) {
			System.out.println("Adding a new connection: " + temps[1] + ":" + temps[2] + " to " +
			                   m.address.getHostAddress() + ":" + m.port);
			Entry<String, Integer> peer1 = new AbstractMap.SimpleEntry<>(temps[1], Integer.valueOf(temps[2]));
			Entry<String, Integer> peer2 = new AbstractMap.SimpleEntry<>(m.address.getHostAddress(), m.port);
			Entry<Entry<String, Integer>, Entry<String, Integer>> record = new AbstractMap.SimpleEntry<>(peer1, peer2);
			Entry<Entry<Entry<String, Integer>, Entry<String, Integer>>, Integer> connect = new AbstractMap.SimpleEntry<>(record, 1);
			Entry<Entry<String, Integer>, Entry<String, Integer>> reverseRecord = new AbstractMap.SimpleEntry<>(peer2, peer1);
			Entry<Entry<Entry<String, Integer>, Entry<String, Integer>>, Integer> reverseConnect = new AbstractMap.SimpleEntry<>(reverseRecord, 1);
      int flag = 1;
			for (int i = 0; i < routingTable.size(); i++)
				if (routingTable.get(i).equals(connect) || routingTable.get(i).equals(reverseConnect))
				  flag = 0;
			if (flag == 1) {
				routingTable.add(connect);
				routingTable.add(reverseConnect);
				System.out.println("Success!");
			} else {
				System.out.println("Error! Connection already exists!");
			}
		} else if (temps[0].equals("DOWN")) {
			System.out.println(temps[1] + ":" + temps[2] + " is down!");
			for (int i = 0; i < routingTable.size(); i++)
			  if ((routingTable.get(i).getKey().getKey().getKey().equals(temps[1]) &&
				     routingTable.get(i).getKey().getKey().getValue().equals(Integer.valueOf(temps[2]))) ||
						(routingTable.get(i).getKey().getValue().getKey().equals(temps[1]) &&
						 routingTable.get(i).getKey().getValue().getValue().equals(Integer.valueOf(temps[2]))))
				  routingTable.get(i).setValue(-1);
		} else if (temps[0].equals("UP")) {
			System.out.println(temps[1] + ":" + temps[2] + " is back online!");
			for (int i = 0; i < routingTable.size(); i++)
				if ((routingTable.get(i).getKey().getKey().getKey().equals(temps[1]) &&
						 routingTable.get(i).getKey().getKey().getValue().equals(Integer.valueOf(temps[2]))) ||
						(routingTable.get(i).getKey().getValue().getKey().equals(temps[1]) &&
						 routingTable.get(i).getKey().getValue().getValue().equals(Integer.valueOf(temps[2]))))
				  routingTable.get(i).setValue(1);
		} else if (temps[0].equals("REQ")) {
			String fromIp = m.address.getHostAddress();
			int fromPort = m.port;
			String toIp = temps[1];
			int toPort = Integer.valueOf(temps[2]);
			List<Vertex> vertexs = new ArrayList<Vertex>();  // 用于存放拓扑图节点的 List
			for (int i = 0; i < routingTable.size(); i++) {
				String peer1 = routingTable.get(i).getKey().getKey().getKey() + ":" +
				               routingTable.get(i).getKey().getKey().getValue();
			  String peer2 = routingTable.get(i).getKey().getValue().getKey() + ":" +
				               routingTable.get(i).getKey().getValue().getValue();
				int isPeer1Count = 0, isPeer2Count = 0;
				for (int j = 0; j < vertexs.size(); j++) {
				  if (vertexs.get(j).name.equals(peer1))
					  isPeer1Count = 1;
					if (vertexs.get(j).name.equals(peer2))
					  isPeer2Count = 1;
				}
				Vertex temp;
				if (isPeer1Count == 0) {
					temp = new Vertex(peer1);
					vertexs.add(temp);
				}
				if (isPeer2Count == 0) {
					temp = new Vertex(peer2);
					vertexs.add(temp);
				}
			}
			int edges[][] = new int[vertexs.size()][vertexs.size()];
			for (int i = 0; i < vertexs.size(); i++)
			  for (int j = 0; j < vertexs.size(); j++)
				  edges[i][j] = Integer.MAX_VALUE;
			for (int i = 0; i < vertexs.size(); i++)
			  for (int j = 0; j < vertexs.size(); j++) {
				  String from = vertexs.get(i).name, to = vertexs.get(j).name;
					for (int k = 0; k < routingTable.size(); k++) {
						String peer1 = routingTable.get(k).getKey().getKey().getKey() + ":" +
													 routingTable.get(k).getKey().getKey().getValue();
						String peer2 = routingTable.get(k).getKey().getValue().getKey() + ":" +
													 routingTable.get(k).getKey().getValue().getValue();
					  int isConnect = routingTable.get(k).getValue();
						if (from.equals(peer1) && to.equals(peer2) && isConnect == 1)
						  edges[i][j] = 1;
					}
				}
			Graph temp = new Graph(vertexs, edges);
			temp.printGraph();
			int fromIndex = -1, toIndex = -1;
			for (int i = 0; i < vertexs.size(); i++) {
			  if (vertexs.get(i).name.equals(fromIp + ":" + fromPort))
				  fromIndex = i;
				if (vertexs.get(i).name.equals(toIp + ":" + toPort))
					toIndex = i;
			}
			int minDistance = Integer.MAX_VALUE;
			int minChoice = -1;
			for (int i = 0; i < vertexs.size(); i++) {
				if (edges[fromIndex][i] != Integer.MAX_VALUE) {
					vertexs.get(i).path = 0;
					Graph graph = new Graph(vertexs, edges);
					int distance = graph.search(toIndex);
					if (minDistance > distance) {
						minDistance = distance;
						minChoice = i;
					}
					vertexs.get(i).path = Integer.MAX_VALUE;
				}
			}
			String info[] = vertexs.get(minChoice).name.split(":");
			if (minDistance != 0)
			  sendMessage(m.address.getHostAddress(), m.port, "RES|" + info[0] + "|" + info[1]);
			else
			  sendMessage(m.address.getHostAddress(), m.port, "RES|" + info[0] + "|" + info[1] + "|END");
		}
	}

	// 这个是用来跟其他路由建立连接的
	@Override
	public void connect(String ip, int port) {
		sendMessage(ip, port, "|ACK|");
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

	public static void main(String[] args) {
		try {
			System.out.print("\033\143");
			System.out.println("+--------------------------------+");
			System.out.println("|           CONTROLLER           |");
			System.out.println("+--------------------------------+");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			Controller router = new Controller(2016);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

// 定义拓扑图的网络节点
class Vertex implements Comparable<Vertex> {
  public String name;
  public int path;
  public boolean isMarked;
  public Vertex(String name) {
    this.name = name;
    this.path = Integer.MAX_VALUE;
    isMarked = false;
  }
  public Vertex(String name, int path) {
    this.name = name;
    this.path = path;
    isMarked = false;
  }
  @Override
  public int compareTo(Vertex o) {
    return o.path > path ? -1 : 1;
  }
}

// 定义拓扑图
class Graph {
  private List<Vertex> vertexs;
  private int[][] edges;
  private Queue<Vertex> unVisited;

  public Graph(List<Vertex> vertexs, int[][] edges) {
    this.vertexs = vertexs;
    this.edges = edges;
    initUnVisited();
  }

  public int search(int index) {
    while (!unVisited.isEmpty()) {
      Vertex vertex = unVisited.element();
      vertex.isMarked = true;
      List<Vertex> neighbors = getNeighbors(vertex);
      updatesDistance(vertex, neighbors);
      pop();
    }
		return this.vertexs.get(index).path;
  }

  private void updatesDistance(Vertex vertex, List<Vertex> neighbors) {
    for (Vertex neighbor : neighbors) {
      updateDistance(vertex, neighbor);
    }
  }

  private void updateDistance(Vertex vertex, Vertex neighbor) {
    int distance = getDistance(vertex, neighbor) + vertex.path;
    if (distance < neighbor.path) {
      neighbor.path = distance;
    }
  }

  private void initUnVisited() {
    unVisited = new PriorityQueue<Vertex>();
    for (Vertex v : vertexs) {
      unVisited.add(v);
    }
  }

  private void pop() {
    unVisited.poll();
  }

  private int getDistance(Vertex source, Vertex destination) {
    int sourceIndex = vertexs.indexOf(source);
    int destIndex = vertexs.indexOf(destination);
    return edges[sourceIndex][destIndex];
  }

  private List<Vertex> getNeighbors(Vertex v) {
    List<Vertex> neighbors = new ArrayList<Vertex>();
    int position = vertexs.indexOf(v);
    Vertex neighbor = null;
    int distance;
    for (int i = 0; i < vertexs.size(); i++) {
      if (i == position) {
        continue;
      }
      distance = edges[position][i];
      if (distance < Integer.MAX_VALUE) {
        neighbor = getVertex(i);
        if (!neighbor.isMarked) {
          neighbors.add(neighbor);
        }
      }
    }
    return neighbors;
  }

  private Vertex getVertex(int index) {
    return vertexs.get(index);
  }

	public void printGraph() {
		int verNums = vertexs.size();
		System.out.println("There are " + verNums + " hosts:");
		for (int i = 0; i < verNums; i++)
		  System.out.println(i + ": " + vertexs.get(i).name);
    System.out.println("The topological graph is following:");
		for (int row = 0; row < verNums; row++) {
			for (int col = 0; col < verNums; col++) {
				if(Integer.MAX_VALUE == edges[row][col]){
					System.out.print("X");
					System.out.print(" ");
					continue;
				}
				System.out.print(edges[row][col]);
				System.out.print(" ");
			}
			System.out.println();
		}
  }
}
