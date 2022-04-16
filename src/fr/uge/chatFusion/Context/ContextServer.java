package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.ConnexionReader;
import fr.uge.chatFusion.Reader.Message.FusionMessageReader;
import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Server.Server;
import fr.uge.chatFusion.Utils.Message;
import fr.uge.chatFusion.Utils.MessageFusion;
import fr.uge.chatFusion.Utils.MessagePrivate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

public class ContextServer implements InterfaceContexteServ {
	static private int BUFFER_SIZE = 10_000;

	private final SelectionKey key;
	private final SocketChannel sc;
	private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
	private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
	private final Server server; // we could also have Context as an instance class, which would naturally
	private final MessageReader messageReader = new MessageReader();
	private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
	private final FusionMessageReader fusionMessageReader = new FusionMessageReader();


	private final ConnexionReader connexionReader = new ConnexionReader();
	private final OpReader opReader = new OpReader();

	private boolean closed = false;

	public ContextServer(Server server, SelectionKey key) {
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.server = server;
	}

	public SelectionKey getKey() {
		return key;
	}

	/**
	 * Process the content of bufferIn
	 * <p>
	 * The convention is that bufferIn is in write-mode before the call to process
	 * and after the call
	 */
	private void processInMessage() {
		for (;;) {
			Reader.ProcessStatus status = messageReader.process(bufferIn);

			// Reader.ProcessStatus status = messageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = messageReader.get();
				server.broadcast(value, key);
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

	private void processInPrivateMessage() {
		for (;;) {
			Reader.ProcessStatus status = privateMessageReader.process(bufferIn);

			// Reader.ProcessStatus status = messageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = privateMessageReader.get();
				server.sendPrivateMessage(value, key);
				privateMessageReader.reset();
				break;
			case REFILL:
				return;
			case ERROR:
				silentlyClose();
				return;
			}
		}
	}

	private void processInConnexion() {

		Reader.ProcessStatus status = connexionReader.process(bufferIn);

		// Reader.ProcessStatus status = messageReader.process(bufferIn);
		switch (status) {
		case DONE:
			var value = connexionReader.get();
			verifyConnection(value); // server.broadcast(value);
			connexionReader.reset();
			break;
		case REFILL:
			return;
		case ERROR:
			silentlyClose();
		}
	}

	private void processInDemandFusion() {
		if(!server.isLeader()) {
			System.out.println("PAS LE LEADER JE TRANSMETS A MON LEADER");
		}
		for (;;) {
			Reader.ProcessStatus status = fusionMessageReader.process(bufferIn);

			// Reader.ProcessStatus status = messageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = fusionMessageReader.get();
				verifyServers(value);
				System.out.println("ca a marché ? "+ value.login() );
				fusionMessageReader.reset();
				break;
			case REFILL:
				return;
			case ERROR:
				silentlyClose();
				return;
			}
		}
	}

	private void sendConnect(boolean condition) {
		bufferOut.clear();
		if (condition)
			bufferOut.put((byte) 7);
		else
			bufferOut.put((byte) 8);

		updateInterestOps();
	}

	private void sendResponseFusion(boolean condition) {
		if (condition)
			bufferOut.put((byte) 9);
		else
			bufferOut.put((byte) 10);
		updateInterestOps();
	}

	private void verifyServers(MessageFusion messageFusion) {
		if(server.verifyServers(messageFusion)) {
			sendResponseFusion(true);
		}
		else
			sendResponseFusion(false);
	}

	private void verifyConnection(String login) {
		if (server.register(login, this)) {
			sendConnect(true);
		} else {
			sendConnect(false);
			// silentlyClose();
		}
	}

	private void processIn() {
		for (;;) {
			Reader.ProcessStatus status = opReader.process(bufferIn);

			switch (status) {
			case DONE:
				var opCode = opReader.get();
				switch (opCode) {
				case 0 -> processInConnexion();
				case 2 -> processInMessage();
				case 3 -> processInPrivateMessage();
				case 5 -> {
					System.out.println("Tentative de fusion reçu");
					processInDemandFusion();
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
	 * Add a message to the message queue, tries to fill bufferOut and
	 * updateInterestOps
	 *
	 * @param msg
	 */
	public void queueMessage(Message msg) {

		var buffer = ByteBuffer.allocate(BUFFER_SIZE);
		msg.encode(buffer);
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

	@Override
	public void doRead() throws IOException {
		if (-1 == sc.read(bufferIn)) {
			closed = true;
		}
		processIn();
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

	@Override
	public void sendUserNotFound(MessagePrivate msg) {

		var buffer = msg.createBufferUserNotFound();

		queue.offer(buffer);
		processOut();
		updateInterestOps();
	}
}