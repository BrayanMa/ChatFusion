package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record Merge(String nameServ) implements Message {
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
        bufferOut.put((byte) 15);
        bufferOut.putInt(nameServ.length());
        bufferOut.put(StandardCharsets.UTF_8.encode(nameServ));
        return true;
    }
}
