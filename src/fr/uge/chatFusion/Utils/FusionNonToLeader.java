package fr.uge.chatFusion.Utils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public record FusionNonToLeader(InetSocketAddress leader) implements Message {
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

		bufferOut.putInt(leader.getAddress().getAddress().length);
		bufferOut.put(leader.getAddress().getAddress());
		bufferOut.putInt(leader.getPort());
		return true;
	}
}
