package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record SendConnect(String name) implements Message {
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
        bufferOut.put((byte) 7);

        var nomServEncode = StandardCharsets.UTF_8.encode(name);
        var nomServSize = nomServEncode.remaining();

        bufferOut.putInt(nomServSize);
        bufferOut.put(nomServEncode);
        return true;
    }
}
