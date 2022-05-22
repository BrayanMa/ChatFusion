package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Utils.DemandeFusionNoLeader;
import fr.uge.chatFusion.Utils.Message;
import fr.uge.chatFusion.Utils.MessagePrivate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

public interface InterfaceContexteServ {
    void doRead() throws IOException;
    void doWrite() throws IOException;

    void changeLeaderPaquet(InetSocketAddress addressLeader) throws IOException;



    default void makeConnectionPaquet(String nameServ, InetSocketAddress address, HashMap<String, InterfaceContexteServ> servers) throws IOException{}

    default void sendAskToLeader(DemandeFusionNoLeader demandeFusionNoLeader) throws IOException {}
    default void sendUserNotFound(MessagePrivate msg){}
    void queueMessage(Message msg) ;
    String getServerName();

}
