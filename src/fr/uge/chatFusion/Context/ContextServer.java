package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.ConnexionReader;
import fr.uge.chatFusion.Reader.MessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Server.Server;
import fr.uge.chatFusion.Utils.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

public class ContextServer {
    static private int BUFFER_SIZE = 10_000;

    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private final Server server; // we could also have Context as an instance class, which would naturally
    private final MessageReader messageReader = new MessageReader();
    private final ConnexionReader connexionReader = new ConnexionReader();
    private final OpReader opReader = new OpReader();
    // give access to ServerChatInt.this
    private boolean closed = false;

    public ContextServer(Server server, SelectionKey key) {
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
    }

    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process and
     * after the call
     */
    /*private void processIn() {
        for (; ; ) {
            Reader.ProcessStatus status = messageReader.process(bufferIn);

           // Reader.ProcessStatus status = messageReader.process(bufferIn);
            switch (status) {
                case DONE:
                    var value = messageReader.get();
                    server.broadcast(value);
                    messageReader.reset();
                    break;
                case REFILL:
                    return;
                case ERROR:
                    silentlyClose();
                    return;
            }
        }
    }*/
    private void processInMessage() {

        Reader.ProcessStatus status = connexionReader.process(bufferIn);

        // Reader.ProcessStatus status = messageReader.process(bufferIn);
        switch (status) {
            case DONE:
                var value = connexionReader.get();
                verifyConnection(value);                    //server.broadcast(value);
                connexionReader.reset();
                break;
            case REFILL:
                return;
            case ERROR:
                silentlyClose();
        }
    }

    private void sendConnect(boolean condition) {
        bufferOut.clear();
        if (condition)
            bufferOut.putInt(7);
        else
            bufferOut.putInt(8);

        updateInterestOps();
    }

    private void verifyConnection(String login) {
        if (server.register(login, sc)) {
            sendConnect(true);
        } else {
            sendConnect(false);
            //silentlyClose();
        }
    }

    private void processIn() {
        for (; ; ) {
            Reader.ProcessStatus status = opReader.process(bufferIn);

            switch (status) {
                case DONE:
                    var opCode = opReader.get();
                    switch (opCode) {
                        case 0 -> {
                            processInMessage();
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
        var nickname = StandardCharsets.UTF_8.encode(msg.login());
        var content = StandardCharsets.UTF_8.encode(msg.texte());

        var buffer = ByteBuffer.allocate(2 * Integer.BYTES + nickname.remaining() + content.remaining());
        buffer.putInt(nickname.remaining());
        buffer.put(nickname);
        buffer.putInt(content.remaining());
        buffer.put(content);
        buffer.flip();

        queue.offer(buffer);
        processOut();
        updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
     */
    private void processOut() {
        while (bufferOut.hasRemaining() && !queue.isEmpty()) {
            var msg = queue.peek();
            if (!msg.hasRemaining()) {
                queue.pop();
                continue;
            }
            if (bufferOut.remaining() >= msg.remaining()) {
                bufferOut.put(msg);
            } else {
                var oldLimit = msg.limit();
                msg.limit(bufferOut.remaining());
                bufferOut.put(msg);
                msg.limit(oldLimit);
            }
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
        if (-1 == sc.read(bufferIn)) {
            closed = true;
        }
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

}