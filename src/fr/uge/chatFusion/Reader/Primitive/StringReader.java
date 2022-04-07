package fr.uge.chatFusion.Reader.Primitive;

import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Reader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {

    private enum State {DONE, WAITING, ERROR};

    private State state = State.WAITING;
    private final ByteBuffer internalBuffer = ByteBuffer.allocate(1024); // write-mode
    private String string;
    private IntReader ir = new IntReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if(state == State.DONE || state == State.ERROR){
            throw new IllegalStateException();
        }


        if(ir.process(buffer) != ProcessStatus.DONE)
            return ProcessStatus.REFILL;
        var size = ir.get();
        if( size > 1024 || size < 0){
            state = State.ERROR;
            return ProcessStatus.ERROR;
        }
        internalBuffer.limit(size)   ;


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
        string = StandardCharsets.UTF_8.decode(internalBuffer).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if(state != State.DONE){
            throw new IllegalStateException();
        }
        return string;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        ir.reset();
        internalBuffer.clear();
    }
}

