package fr.uge.chatFusion.Client;

import fr.uge.chatFusion.Context.ContextClient;
import fr.uge.chatFusion.Utils.Message;
import fr.uge.chatFusion.Utils.MessageFichier;
import fr.uge.chatFusion.Utils.MessagePrivate;
import fr.uge.chatFusion.Utils.MessagePublique;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class Client {

	static private final Logger logger = Logger.getLogger(Client.class.getName());
	static private final int BUFFER_SIZE = 10_000;
	private final SocketChannel sc;
	private final Selector selector;
	private final Path path;
	private final InetSocketAddress serverAddress;
	private final String login;
	private final Thread console;
	private final ArrayDeque<Message> queue = new ArrayDeque<>();
	private ContextClient uniqueContext;
	private String servName;

	private final List<MessageFichier> lstFichier = new ArrayList<>();

	public Client(InetSocketAddress serverAddress, Path path, String login) throws IOException {
		Objects.requireNonNull(serverAddress);
		Objects.requireNonNull(path);
		Objects.requireNonNull(login);
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

	/**
	 * Print usage on the console
	 */
	private static void usage() {
		System.out.println("Usage : Client hostname port path login");
	}

	/**
	 * Run the console
	 */
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

	/**
	 * Send a command to a server
	 * @param msg String message
	 * @throws InterruptedException
	 */
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
				if (!msg.matches("/.*:.*")) {
					break;
				}
				var tab3 = msg.split(":");
				var tab4 = tab3[1].split(" ", 2);
				
				

				var lstBlock = new ArrayList<byte[]>();

				var file = path.toAbsolutePath().resolve(tab4[1]).toFile();
				FileInputStream fileInputStream;
				try {
					fileInputStream = new FileInputStream(file);
				} catch (FileNotFoundException e) {
					System.out.println("FileNotFound");
					return;
				}
				int n;

				var blockList = new ArrayList<Byte>();
				byte[] block ; 
				try {

					while ((n = fileInputStream.read()) != -1) {
						blockList.add((byte) n);
						if (blockList.size() == 5000) {
							block = new byte[5000];
							for(var i = 0; i<5000;i++) {
								block[i] =blockList.get(i);
							}
							lstBlock.add(block);
							blockList.clear();
						}
					}
					if (!blockList.isEmpty()) {
						block = new byte[blockList.size()];
						for(var i = 0; i<blockList.size();i++) {
							block[i] =blockList.get(i);
						}
						lstBlock.add(block);
						blockList.clear();
					}
					for (var tabByte : lstBlock) {
						queue.addLast(new MessageFichier(servName, login, tab4[0], tab3[0].replace("/", ""), tab4[1],
								lstBlock.size(), tabByte));
					}
					fileInputStream.close();
				} catch (IOException e) {
					return;
				}
				break;
			case '@':
				if (!msg.matches("@.*:.*")) {
					break;
				}
				var tab1 = msg.split(":");
				var tab2 = tab1[1].split(" ", 2);
				queue.addLast(new MessagePrivate(servName, login, tab2[0], tab1[0].replace("@", ""), tab2[1]));

				break;
			default:
				queue.addLast(new MessagePublique(login, msg, servName));
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

	/**
	 * Launch the client
	 * @throws IOException
	 */
	public void launch() throws IOException {
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new ContextClient(key, logger, this);
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

	/**
	 * Close the connexion client
	 * @param key Selection key
	 */
	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Put a name to a serveur
	 * @param name String name
	 */
	public void servName(String name) {
		Objects.requireNonNull(name);
		servName = name;
	}

	/**
	 * Send a file
	 * @param value MessageFichier file
	 */
	public void sendFileMessage(MessageFichier value) {
		Objects.requireNonNull(value);
		lstFichier.add(value);
		if (lstFichier.size() == value.nb_blocks()) {

			var file = path.toAbsolutePath().resolve(value.fileName()).toFile();
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(file);
				for (var message : lstFichier) {
					fileOutputStream.write(message.blocks());
				}
				fileOutputStream.close();
			} catch (FileNotFoundException e) {
				System.out.println("file not found");
				lstFichier.clear();
				return;
			} catch (IOException e) {
				System.out.println("error while writing data");
				lstFichier.clear();
				return;
			}

			lstFichier.clear();
		}

	}
}
