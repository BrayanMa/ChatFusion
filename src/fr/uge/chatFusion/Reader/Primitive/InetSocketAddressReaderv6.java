package fr.uge.chatFusion.Reader.Primitive;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Objects;

import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;

public class InetSocketAddressReaderv6 implements Reader<InetSocketAddress> {
	private State state = State.WAITING;
	private final ByteBuffer internalBuffer = ByteBuffer.allocate(6 + Integer.BYTES); // write-mode
	
	private InetSocketAddress value;

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		bb.flip();
		try {
			if (bb.remaining() <= internalBuffer.remaining()) {
				internalBuffer.put(bb);
			} else {
				var oldLimit = bb.limit();
				bb.limit(internalBuffer.remaining());
				internalBuffer.put(bb);
				bb.limit(oldLimit);
			}
		} finally {
			bb.compact();
		}
		if (internalBuffer.hasRemaining()) {
			return ProcessStatus.REFILL;
		}
		state = State.DONE;
		internalBuffer.flip();
		
		byte[] ipAddr = new byte[6];
		for (int i = 0; i < 6; i++) {
			ipAddr[i] = internalBuffer.get();
		}
		try {
			value = new InetSocketAddress(InetAddress.getByAddress(ipAddr),internalBuffer.getInt());

		} catch (UnknownHostException e) {
			return ProcessStatus.ERROR;
		}
		
		return ProcessStatus.DONE;
	}

	@Override
	public InetSocketAddress get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return value;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		internalBuffer.clear();
	}
}
