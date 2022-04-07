package fr.uge.chatFusion.Reader;

import fr.uge.chatFusion.Utils.MessagePublique;
import fr.uge.chatFusion.Utils.MessagePrivate;

import java.nio.ByteBuffer;

public class PrivateMessageReader implements Reader<MessagePrivate> {
    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private String loginDest;
    private String servDest;
    private String login;
    private String msg;
    private MessagePrivate messagePrivate;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var loginDestState = stringReader.process(bb);
        if (loginDestState != ProcessStatus.DONE) {
            return loginDestState;
        }
        loginDest = stringReader.get();
        stringReader.reset();
        var servDestState = stringReader.process(bb);
        if (servDestState != ProcessStatus.DONE) {
            return servDestState;
        }
        servDest = stringReader.get();
        stringReader.reset();
        
        var loginState = stringReader.process(bb);
        if (loginState != ProcessStatus.DONE) {
            return loginState;
        }
        login = stringReader.get();
        stringReader.reset();
        var msgState = stringReader.process(bb);
        if (msgState != ProcessStatus.DONE) {
            return msgState;
        }
        msg = stringReader.get();
        state = State.DONE;

        messagePrivate = new MessagePrivate(loginDest, servDest, login, msg);
        return ProcessStatus.DONE;
    }

    @Override
    public MessagePrivate get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return messagePrivate;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        stringReader.reset();
    }
}
