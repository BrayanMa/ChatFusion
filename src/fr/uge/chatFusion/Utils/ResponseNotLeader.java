package fr.uge.chatFusion.Utils;

import fr.uge.chatFusion.Server.Server;

import java.nio.ByteBuffer;
import java.util.Objects;

public record ResponseNotLeader(Server server) implements Message {
    @Override
    public String login() {
        return "";
    }

    @Override
    public String msg() {
        return "";
    }

    @Override
    public boolean encode(ByteBuffer bufferOut) {
        Objects.requireNonNull(bufferOut);
        bufferOut.put((byte) 11);
        bufferOut.putInt(server.getLeader().getAddress().getAddress().length);
        bufferOut.put(server.getLeader().getAddress().getAddress());
        bufferOut.putInt(server.getLeader().getPort());
        return true;
    }
}
