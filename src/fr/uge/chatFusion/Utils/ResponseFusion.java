package fr.uge.chatFusion.Utils;

import fr.uge.chatFusion.Context.ContextServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public record ResponseFusion(InetSocketAddress leader, Map<String, InetSocketAddress> servers)  implements Message {
    @Override
    public String login() {
        return "";
    }

    @Override
    public String msg() {
        return "";
    }

    @Override
    public void encode(ByteBuffer bufferOut) {
        bufferOut.put((byte) 9);
        bufferOut.putInt(leader.getAddress().getAddress().length);
        bufferOut.put(leader.getAddress().getAddress());
        bufferOut.putInt(leader.getPort());
        bufferOut.putInt(servers.size());
        for (var entry : servers.entrySet()) {
            bufferOut.putInt(entry.getKey().length());
            bufferOut.put(StandardCharsets.UTF_8.encode(entry.getKey()));
            bufferOut.putInt(entry.getValue().getAddress().getAddress().length);
            bufferOut.put(entry.getValue().getAddress().getAddress());
            bufferOut.putInt(entry.getValue().getPort());
        }
    }
}
