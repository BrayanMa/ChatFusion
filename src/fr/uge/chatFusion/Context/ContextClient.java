package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.Message.FileMessageReader;
import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Client.Client;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader.ProcessStatus;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Utils.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
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
	private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
	private final StringReader stringreader = new StringReader();
	private final FileMessageReader fileMessageReader = new FileMessageReader();

	private final OpReader opReader = new OpReader();
	private final Logger logger;
	private final Client client;

	private boolean closed = false;


	public ContextClient(SelectionKey key, Logger logger, Client client) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(logger);
		Objects.requireNonNull(client);
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.logger = logger;
		this.client = client;
	}

	/**
	 * Create connexion paquet to initiate the connexion with a server
	 * 
	 * @param login String login
	 * @throws IOException
	 */
	private void makeConnectionPaquet(String login) throws IOException {
		bufferOut.clear();
		bufferOut.put((byte) 0);
		bufferOut.putInt(login.length());
		bufferOut.put(StandardCharsets.UTF_8.encode(login));
		doWrite();
	}

	/**
	 * Process a public message when the client receive it
	 */
	private void processInMessage() {
		for (;;) {
			Reader.ProcessStatus status = messageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = messageReader.get();
				System.out.println(value.nomServ() + ":" + value.login() + " \n\t↳ " + value.msg());
				messageReader.reset();
				return;
			case REFILL:
				return;
			case ERROR:
				silentlyClose();
				return;
			}
		}
	}

	/**
	 * Process a private message when the client receive it
	 */
	private void processInPrivateMessage() {
		for (;;) {
			Reader.ProcessStatus status = privateMessageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = privateMessageReader.get();
				System.out.println("Private message from : " + value.servEmetteur() + ":" + value.login() + " \n\t↳ "
						+ value.msg());
				privateMessageReader.reset();
				return;
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
		for (;;) {
			Reader.ProcessStatus status = opReader.process(bufferIn);
			switch (status) {
			case DONE:
				var opCode = opReader.get();
				switch (opCode) {
				case 7 -> {
					logger.info("Connection établie");
					processInConnection();
				}
				case 8 -> {
					logger.warning("Erreur de connection");
					silentlyClose();
				}
				case 2 -> processInMessage();
				case 3 -> processInPrivateMessage();
				case 4 -> processInFileMessage();
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
	}

	/**
	 * Process a file when the client receive it
	 */
	private void processInFileMessage() {
		for (;;) {
			Reader.ProcessStatus status = fileMessageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = fileMessageReader.get();
				client.sendFileMessage(value);
				fileMessageReader.reset();
				return;
			case REFILL:
				return;
			case ERROR:
				silentlyClose();
				return;
			}
		}
	}

	/**
	 * Process a connexion message when the client receive it
	 */
	private void processInConnection() {

		var nomServState = stringreader.process(bufferIn);
		if (nomServState != ProcessStatus.DONE) {
			client.servName("unknow");
			return;
		}
		client.servName(stringreader.get());
		stringreader.reset();
	}

	/**
	 * Put a message in the queue
	 */
	public void queueMessage(Message msg) {
		Objects.requireNonNull(msg);
		queue.add(msg);
		processOut();
		updateInterestOps();
	}

	/**
	 * Try to fill bufferOut from the message queue
	 */
	private void processOut() {
		while (!queue.isEmpty()) {
			var msg = queue.peek();
			// var size = msg.login().length() + msg.texte().length() + 2 * Integer.BYTES;
			if (msg.encode(bufferOut)) {
				queue.pop();
			}
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
		if (closed) {
			silentlyClose();
			return;
		}
		if (bufferOut.position() != 0) {
			key.interestOps(SelectionKey.OP_WRITE);
		} else {
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	private void silentlyClose() {
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Read
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
	 * Write
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

	/**
	 * Connect
	 * 
	 * @param login String login
	 * @throws IOException
	 */
	public void doConnect(String login) throws IOException {
		Objects.requireNonNull(login);
		if (!sc.finishConnect()) {
			return;
		}
		logger.log(Level.INFO, "Tentative de connection au serveur...");
		makeConnectionPaquet(login);
		key.interestOps(SelectionKey.OP_WRITE);
	}

}
