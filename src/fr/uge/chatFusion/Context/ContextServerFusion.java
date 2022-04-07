package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Utils.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContextServerFusion implements InterfaceContexteServ {
    static private final int BUFFER_SIZE = 10_000;
    private final Logger logger;

    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private final MessageReader messageReader = new MessageReader();
    private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
    private final OpReader opReader = new OpReader();

    private boolean closed = false;

    public ContextServerFusion(SelectionKey key, Logger logger) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.logger = logger;
    }

    private void makeConnectionPaquet(String nameServ, Map<InetSocketAddress, String> servers) throws IOException {
        bufferOut.clear();
        // bufferOut.flip();
        bufferOut.put((byte) 5);
        bufferOut.putInt(nameServ.length());
        bufferOut.put(StandardCharsets.UTF_8.encode(nameServ));
        bufferOut.putInt(servers.size());
        for (var entry : servers.entrySet()) {
            bufferOut.putInt(entry.getValue().length());
            bufferOut.put(StandardCharsets.UTF_8.encode(entry.getValue()));
            bufferOut.putInt(entry.getKey().getAddress().getAddress().length);
            bufferOut.put(entry.getKey().getAddress().getAddress());
        }
        doWrite();
    }

    /*private void processIn() {
        for (;;) {
            Reader.ProcessStatus status = opReader.process(bufferIn);
            switch (status) {
                case DONE:
                    var opCode = opReader.get();
                    switch (opCode) {
                        case 7 -> logger.info("Connection établie");
                        case 8 -> {
                            logger.warning("Erreur de connection");
                            silentlyClose();
                        }
                        case 2 -> processInMessage();
                        case 3 -> processInPrivateMessage();
                        case 11 -> logger.info("Destinataire n'a pas été trouvé");
                    }
                    opReader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }*/

   /* public void queueMessage(Message msg) {
        // TODO
        queue.add(msg);
        processOut();
        updateInterestOps();
    }*/

    private void processOut() {

        updateInterestOps();

    }

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */

    private void updateInterestOps() {
        int interestOps = 0;
        if (!closed && bufferIn.hasRemaining()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (bufferOut.position() != 0) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        if (interestOps == 0) {
            silentlyClose();
            return;
        }
        key.interestOps(interestOps);
    }

    private void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    @Override
    public void doRead() throws IOException {
        if (sc.read(bufferIn) == -1)
            closed = true;
        //processIn();
        updateInterestOps();
    }

    @Override
    public void doWrite() throws IOException {
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    public void doConnect(String nameServ, Map<InetSocketAddress, String> servers) throws IOException {
        if (!sc.finishConnect()) {
            return;
        }
        logger.log(Level.INFO, "Tentative de connection au serveur...");
        makeConnectionPaquet(nameServ, servers);
        key.interestOps(SelectionKey.OP_WRITE);
    }

}
