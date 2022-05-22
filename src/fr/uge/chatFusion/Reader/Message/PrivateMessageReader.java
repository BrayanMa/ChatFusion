package fr.uge.chatFusion.Reader.Message;

import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Utils.MessagePrivate;

import java.nio.ByteBuffer;
import java.util.Objects;

public class PrivateMessageReader implements Reader<MessagePrivate> {
    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private String loginDest;
    private String servDest;
    private String servEmetteur;
    private String login;
    private String msg;
    private MessagePrivate messagePrivate;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        Objects.requireNonNull(bb);
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        
        var servEmetteurState = stringReader.process(bb);
        if (servEmetteurState != ProcessStatus.DONE) {
            return servEmetteurState;
        }
        servEmetteur = stringReader.get();
        stringReader.reset();
        
        var loginState = stringReader.process(bb);
        if (loginState != ProcessStatus.DONE) {
            return loginState;
        }
        login = stringReader.get();
        stringReader.reset();
        
        
        var servDestState = stringReader.process(bb);
        if (servDestState != ProcessStatus.DONE) {
            return servDestState;
        }
        servDest = stringReader.get();
        stringReader.reset();
        
        
        var loginDestState = stringReader.process(bb);
        if (loginDestState != ProcessStatus.DONE) {
            return loginDestState;
        }
        loginDest = stringReader.get();
        stringReader.reset();
        
        
        
        var msgState = stringReader.process(bb);
        if (msgState != ProcessStatus.DONE) {
            return msgState;
        }
        msg = stringReader.get();
        state = State.DONE;

        messagePrivate = new MessagePrivate(servEmetteur, login,servDest,loginDest, msg);
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
