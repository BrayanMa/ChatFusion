package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

public record ResponseFusionOk(String name, InetSocketAddress addressEmetteur, Set<String> servers)  implements Message {
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
        bufferOut.put((byte) 9);

        bufferOut.putInt(name.length());
        bufferOut.put(StandardCharsets.UTF_8.encode(name));

        bufferOut.putInt(addressEmetteur.getAddress().getAddress().length);
        bufferOut.put(addressEmetteur.getAddress().getAddress());
        bufferOut.putInt(addressEmetteur.getPort());

        bufferOut.putInt(servers.size());
        for (var entry : servers) {
            bufferOut.putInt(entry.length());
            bufferOut.put(StandardCharsets.UTF_8.encode(entry));
        }
        
        return true;
    }
}
