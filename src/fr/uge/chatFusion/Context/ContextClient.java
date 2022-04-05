package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.MessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Utils.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContextClient {
    static private final int BUFFER_SIZE = 10_000;

    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private final MessageReader messageReader = new MessageReader();
    private final OpReader opReader = new OpReader();
    private final Logger logger;

    private boolean closed = false;

    public ContextClient(SelectionKey key, Logger logger) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.logger = logger;
    }

    private void makeConnectionPaquet(String login) throws IOException {
        bufferOut.clear();
        //bufferOut.flip();
        bufferOut.putInt(0);
        bufferOut.putInt(login.length());
        bufferOut.put(StandardCharsets.UTF_8.encode(login));
        doWrite();
    }

    private void processInMessage() {
        for (; ; ) {
            Reader.ProcessStatus status = messageReader.process(bufferIn);
            switch (status) {
                case DONE:
                    var value = messageReader.get();
                    System.out.println(value.login() + " \n\t↳ " + value.texte());
                    messageReader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }

    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process
     * and after the call
     */
    private void processIn() {
        for (; ; ) {
            Reader.ProcessStatus status = opReader.process(bufferIn);
            switch (status) {
                case DONE:
                    var opCode = opReader.get();
                    switch (opCode){
                        case 7 -> logger.info("Connection établie");
                        case 8 -> {logger.warning("Erreur de connection");
                        silentlyClose();
                        }
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
    }

    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     *
     * @param msg
     */
    public void queueMessage(Message msg) {
        // TODO
        queue.add(msg);
        processOut();
        updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
     */
    private void processOut() {
        // TODO
        while (!queue.isEmpty()) {
            var msg = queue.peek();
            //var size = msg.login().length() + msg.texte().length() + 2 * Integer.BYTES;
            var login = StandardCharsets.UTF_8.encode(msg.login());
            var sizeLogin = login.remaining();
            var content = StandardCharsets.UTF_8.encode(msg.texte());
            var sizeContent = content.remaining();
            var size = sizeLogin + sizeContent + 2 * Integer.BYTES;


            if (size > 1024)
                throw new IllegalStateException();
            if (size > bufferOut.remaining()) {
                return;
            }
            bufferOut.putInt(sizeLogin);
            bufferOut.put(login);
            bufferOut.putInt(sizeContent);
            bufferOut.put(content);
            queue.pop();
            updateInterestOps();

        }
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

    /**
     * Performs the read action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    public void doRead() throws IOException {
        if (sc.read(bufferIn) == -1)
            closed = true;
        processIn();
        updateInterestOps();
    }

    /**
     * Performs the write action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     */

    public void doWrite() throws IOException {
        bufferOut.flip();
        sc.write(bufferOut);
        bufferOut.compact();
        processOut();
        updateInterestOps();
    }

    public void doConnect(String login) throws IOException {
        // TODO
        if (!sc.finishConnect()) {
            return;
        }
        logger.log(Level.INFO, "Tentative de connection au serveur...");
        makeConnectionPaquet(login);
        key.interestOps(SelectionKey.OP_WRITE);
    }

}
