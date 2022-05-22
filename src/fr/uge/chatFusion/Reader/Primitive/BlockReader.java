package fr.uge.chatFusion.Reader.Primitive;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.uge.chatFusion.Reader.Reader;

public class BlockReader implements Reader<ByteBuffer> {

	private enum State {
		DONE, WAITING, ERROR
	};

	private State state = State.WAITING;

	private final ByteBuffer internalBuffer = ByteBuffer.allocate(5000); // write-mode
	private ByteBuffer block;
	private IntReader ir = new IntReader();

	@Override
	public ProcessStatus process(ByteBuffer buffer) {
		Objects.requireNonNull(buffer);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}

		if (ir.process(buffer) != ProcessStatus.DONE)
			return ProcessStatus.REFILL;
		var size = ir.get();
		if (size > 5000 || size < 0) {
			state = State.ERROR;
			return ProcessStatus.ERROR;
		}
		internalBuffer.limit(size);

		buffer.flip();
		try {
			if (buffer.remaining() <= internalBuffer.remaining()) {
				internalBuffer.put(buffer);
			} else {
				var oldLimit = buffer.limit();
				buffer.limit(internalBuffer.remaining());
				internalBuffer.put(buffer);
				buffer.limit(oldLimit);
			}
		} finally {
			buffer.compact();
		}
		if (internalBuffer.hasRemaining()) {
			return ProcessStatus.REFILL;
		}
		state = State.DONE;
		internalBuffer.flip();

		block = ByteBuffer.allocate(internalBuffer.capacity());
		internalBuffer.rewind();// copy from the beginning
		block.put(internalBuffer);
		internalBuffer.rewind();
		block.flip();
		
		return ProcessStatus.DONE;
	}

	@Override
	public ByteBuffer get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return block;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		ir.reset();
		internalBuffer.clear();
		block.clear();
	}

}
