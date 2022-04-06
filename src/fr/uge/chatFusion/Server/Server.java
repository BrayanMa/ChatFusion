package fr.uge.chatFusion.Server;

import fr.uge.chatFusion.Context.ContextServer;
import fr.uge.chatFusion.Reader.MessageReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Utils.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Server {


    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Map<SocketChannel, String> clients;

    public Server(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.clients = new HashMap<>();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void updateClients(){
        for(var sc : clients.keySet()){
            var tmp = selector.keys().stream().map(x -> (Channel) x.channel()).toList();
            if(!tmp.contains(sc))
                clients.remove(sc);
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        updateClients();
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            System.out.println("fin");
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((ContextServer) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((ContextServer) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            System.out.println("fin");

            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        SocketChannel sc = serverSocketChannel.accept();
        if (sc == null) {
            logger.warning("selector was wrong");
            return;
        }
        sc.configureBlocking(false);
        var key1 = sc.register(selector, SelectionKey.OP_READ);
        key1.attach(new ContextServer(this, key1));
    }

    public void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a message to all connected clients queue
     *
     * @param msg
     */
    public void broadcast(Message msg) {
        for(var key : selector.keys()){
            ContextServer c = (ContextServer) key.attachment();
            if(Objects.isNull(c))
                continue;
            c.queueMessage(msg);
        }
    }

    public boolean register(String login, SocketChannel sc){
        if(clients.containsValue(login)){
            return false;
        } else {
            clients.put(sc, login);
            return true;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new Server(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : Server port");
    }
}

