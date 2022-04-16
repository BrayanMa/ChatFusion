package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

    public record MessageFusionTransitToLeader(InetSocketAddress adr) implements Message{
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
            bufferOut.put((byte) 20);
            bufferOut.putInt(adr.getAddress().getAddress().length);
            bufferOut.put(adr.getAddress().getAddress());
            bufferOut.putInt(adr.getPort());
        }
    }
