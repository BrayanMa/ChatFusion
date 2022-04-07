package fr.uge.chatFusion.Reader;


import java.nio.ByteBuffer;

public class OpReader implements Reader<Byte>{

    private State state = State.WAITING;
    private final ByteReader byteReader = new ByteReader();
    private byte opCode;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
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
