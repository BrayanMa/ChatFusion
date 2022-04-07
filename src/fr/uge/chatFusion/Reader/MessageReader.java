package fr.uge.chatFusion.Reader;

import fr.uge.chatFusion.Utils.MessagePublique;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<MessagePublique> {

    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private String login;
    private String texte;
    private MessagePublique message;


    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var loginState = stringReader.process(buffer);
        if (loginState != ProcessStatus.DONE) {
            return loginState;
        }
        login = stringReader.get();
        stringReader.reset();
        var texteState = stringReader.process(buffer);
        if (texteState != ProcessStatus.DONE) {
            return texteState;
        }
        texte = stringReader.get();
        state = State.DONE;
        message = new MessagePublique(login, texte);
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