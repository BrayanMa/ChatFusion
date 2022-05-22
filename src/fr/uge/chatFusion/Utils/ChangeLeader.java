package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public record ChangeLeader(InetSocketAddress addressLeader) implements Message {
    @Override
    public String login() {
        return null;
    }

    @Override
    public String msg() {
        return null;
    }

    @Override
    public boolean encode(ByteBuffer bufferOut) {
        Objects.requireNonNull(bufferOut);
        bufferOut.put((byte) 14);

        bufferOut.putInt(addressLeader.getAddress().getAddress().length);
        bufferOut.put(addressLeader.getAddress().getAddress());
        bufferOut.putInt(addressLeader.getPort());
        return true;
    }
}
