package fr.uge.chatFusion.Reader;


import fr.uge.chatFusion.Reader.Primitive.ByteReader;

import java.nio.ByteBuffer;
import java.util.Objects;

public class OpReader implements Reader<Byte>{

    private State state = State.WAITING;
    private final ByteReader byteReader = new ByteReader();
    private byte opCode;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        Objects.requireNonNull(bb);
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var opCodeState = byteReader.process(bb);
        if(opCodeState != ProcessStatus.DONE) {
            return opCodeState;
        }
        opCode = byteReader.get();
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public Byte get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return opCode;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        byteReader.reset();
    }
}
