package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public record MessageFusion(String login, InetSocketAddress addressEmetteur, int nbrServ, HashMap<String, InetSocketAddress> servers) implements Message{

	@Override
	public String msg() {
		return "";
	}
	
	@Override
	public void encode(ByteBuffer bufferOut) {
		bufferOut.put((byte) 5);
		bufferOut.putInt(login.length());
		bufferOut.put(StandardCharsets.UTF_8.encode(login));

		bufferOut.putInt(addressEmetteur.getAddress().getAddress().length);
		bufferOut.put(addressEmetteur.getAddress().getAddress());
		bufferOut.putInt(addressEmetteur.getPort());

		bufferOut.putInt(servers.size());
		for (var entry : servers.entrySet()) {
			bufferOut.putInt(entry.getKey().length());
			bufferOut.put(StandardCharsets.UTF_8.encode(entry.getKey()));
			System.out.println("--->" + entry.getValue().getAddress().getAddress().length);
			bufferOut.putInt(entry.getValue().getAddress().getAddress().length);
			bufferOut.put(entry.getValue().getAddress().getAddress());
			bufferOut.putInt(entry.getValue().getPort());
		}
	}

	
	

	
	
	
}
