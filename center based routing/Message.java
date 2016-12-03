import java.net.*;

class Message {
	public InetAddress address;
	public int port;
	public String message;

	public Message(InetAddress address, String message) {
		this.address = address;
		String[] temps = message.split("!");
		this.port = Integer.valueOf(temps[0]);
		this.message = "";
		for (int i = 1; i < temps.length; i++)
			this.message += temps[i];
	}
}