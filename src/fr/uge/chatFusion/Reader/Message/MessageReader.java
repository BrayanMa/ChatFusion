package fr.uge.chatFusion.Reader.Message;

import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Utils.MessagePublique;

import java.nio.ByteBuffer;
import java.util.Objects;

public class MessageReader implements Reader<MessagePublique> {

    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private String login;
    private String texte;
    private String nameServ;
    
    private MessagePublique message;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var loginState = stringReader.process(buffer);
        if (loginState != ProcessStatus.DONE) {
            return loginState;
        }
        login = stringReader.get();
        stringReader.reset();
        
        var nameServState = stringReader.process(buffer);
        if (nameServState != ProcessStatus.DONE) {
            return nameServState;
        }
        nameServ = stringReader.get();
        stringReader.reset();
        
        
        var texteState = stringReader.process(buffer);
        if (texteState != ProcessStatus.DONE) {
            return texteState;
        }
        texte = stringReader.get();
        state = State.DONE;
        message = new MessagePublique(login, texte, nameServ);
        return ProcessStatus.DONE;
    }

    @Override
    public MessagePublique get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        stringReader.reset();
    }
}