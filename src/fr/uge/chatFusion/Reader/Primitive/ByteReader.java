package fr.uge.chatFusion.Reader.Primitive;

import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ByteReader implements Reader<Byte> {

	private State state = State.WAITING;
	private final ByteBuffer internalBuffer = ByteBuffer.allocate(Byte.BYTES); // write-mode
	private byte value;

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
		value = internalBuffer.get();
		return ProcessStatus.DONE;
	}

	@Override
	public Byte get() {
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