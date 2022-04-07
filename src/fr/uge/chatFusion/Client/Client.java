package fr.uge.chatFusion.Client;

import fr.uge.chatFusion.Context.ContextClient;
import fr.uge.chatFusion.Utils.Message;
import fr.uge.chatFusion.Utils.MessagePrivate;
import fr.uge.chatFusion.Utils.MessagePublique;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Logger;

public class Client {

    static private final Logger logger = Logger.getLogger(Client.class.getName());
    static private int BUFFER_SIZE = 10_000;
    private final SocketChannel sc;
    private final Selector selector;
    private final Path path;
    private final InetSocketAddress serverAddress;
    private final String login;
    private final Thread console;
    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private ContextClient uniqueContext;

    public Client(InetSocketAddress serverAddress, Path path, String login) throws IOException {
        this.path = path;
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new Client(new InetSocketAddress(args[0], Integer.parseInt(args[1])), Path.of(args[2]), args[3]).launch();
    }

    private static void usage() {
        System.out.println("Usage : Client hostname port path login");
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }


    private void sendCommand(String msg) throws InterruptedException {
        if (StandardCharsets.UTF_8.encode(msg).remaining() > BUFFER_SIZE) {
            logger.warning("Message too long");
            return;
        }
        synchronized (queue) {
            if (msg.length() <= 0) {
                return;
            }
            switch (msg.charAt(0)) {
                case '/':
                    break;
                case '@':
                    if (!msg.matches("@.*:.*")) {
                        break;
                    }
                    var tab1 = msg.split(":");
                    var tab2 = tab1[1].split(" ", 2);
                    queue.addLast(new MessagePrivate(tab1[0].replace("@", ""), tab2[0], login, tab2[1]));

                    break;
                default:
                    queue.addLast(new MessagePublique(login, msg));
            }
            selector.wakeup();
        }
    }

    /**
     * Processes the command from the BlockingQueue
     */

    private void processCommands() {
        synchronized (queue) {
            while (!queue.isEmpty())
                uniqueContext.queueMessage(queue.poll());
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new ContextClient(key, logger);
        key.attach(uniqueContext);
        sc.connect(serverAddress);

        console.start();
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect(login);
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
}
