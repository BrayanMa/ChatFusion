package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public record DemandeFusionNoLeader(InetSocketAddress addressEmetteur) implements Message {
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

        bufferOut.put((byte) 12);
		
        bufferOut.putInt(addressEmetteur.getAddress().getAddress().length);
        bufferOut.put(addressEmetteur.getAddress().getAddress());
        bufferOut.putInt(addressEmetteur.getPort());
        return true;
    }
}
