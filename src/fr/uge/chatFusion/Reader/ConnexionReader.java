package fr.uge.chatFusion.Reader;

import fr.uge.chatFusion.Context.Context;
import fr.uge.chatFusion.Utils.Connexion;
import fr.uge.chatFusion.Utils.Message;

import java.nio.ByteBuffer;

public class ConnexionReader implements Reader<String>{


    private State state = State.WAITING;
    //private final IntReader intReader = new IntReader();
    private final StringReader stringReader = new StringReader();
    private String login;
    //private int opCode;
    //private Connexion connexion;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
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
