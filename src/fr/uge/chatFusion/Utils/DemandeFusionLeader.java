package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

public record DemandeFusionLeader(String name, InetSocketAddress addressEmetteur, Set<String> servers) implements Message {
    @Override
    public String login() {
        return name;
    }

    @Override
    public String msg() {
        return null;
    }

    @Override
    public boolean encode(ByteBuffer bufferOut) {
        Objects.requireNonNull(bufferOut);

        bufferOut.put((byte) 5);
        
        
        var nameEncode = StandardCharsets.UTF_8.encode(name);
		var nameSize = nameEncode.remaining();
		
		
        bufferOut.putInt(nameSize);
        bufferOut.put(nameEncode);

        bufferOut.putInt(addressEmetteur.getAddress().getAddress().length);
        bufferOut.put(addressEmetteur.getAddress().getAddress());
        bufferOut.putInt(addressEmetteur.getPort());

        bufferOut.putInt(servers.size());
        for (var entry : servers) {
        	
        	var nameServEncode = StandardCharsets.UTF_8.encode(entry);
     		var nameServSize = nameServEncode.remaining();
     		
            bufferOut.putInt(nameServSize);
            bufferOut.put(nameServEncode);
        }
        
        return true;
    }
}
