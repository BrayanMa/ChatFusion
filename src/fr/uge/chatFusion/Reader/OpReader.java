package fr.uge.chatFusion.Reader;

import fr.uge.chatFusion.Utils.Connexion;

import java.nio.ByteBuffer;

public class OpReader implements Reader<Integer>{

    private State state = State.WAITING;
    private final IntReader intReader = new IntReader();
    private int opCode;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var opCodeState = intReader.process(bb);
        if(opCodeState != ProcessStatus.DONE) {
            return opCodeState;
        }
        opCode = intReader.get();
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public Integer get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return opCode;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        intReader.reset();
    }
}
