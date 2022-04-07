package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Utils.MessagePrivate;

import java.io.IOException;

public interface InterfaceContexteServ {
    void doRead() throws IOException;
    void doWrite() throws IOException;

    default void sendUserNotFound(MessagePrivate msg){}
}
