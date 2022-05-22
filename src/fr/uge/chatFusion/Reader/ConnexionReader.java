package fr.uge.chatFusion.Reader;

import fr.uge.chatFusion.Reader.Primitive.StringReader;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ConnexionReader implements Reader<String>{


    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private String login;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        Objects.requireNonNull(bb);
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var loginState = stringReader.process(bb);
        if (loginState != ProcessStatus.DONE) {
            return loginState;
        }
        login = stringReader.get();
        stringReader.reset();

        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return login;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        stringReader.reset();
    }
}
