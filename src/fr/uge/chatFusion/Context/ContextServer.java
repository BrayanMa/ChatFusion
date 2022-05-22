package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.ConnexionReader;
import fr.uge.chatFusion.Reader.Message.FileMessageReader;
import fr.uge.chatFusion.Reader.Message.FusionLeaderReader;
import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.Response.ResponseNonLeader;
import fr.uge.chatFusion.Server.Server;
import fr.uge.chatFusion.Utils.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Objects;

public class ContextServer implements InterfaceContexteServ {
	static private final int BUFFER_SIZE = 10_000;

	private final SelectionKey key;
	private final SocketChannel sc;
	private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
	private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
	private final Server server; // we could also have Context as an instance class, which would naturally
	private final MessageReader messageReader = new MessageReader();

	private final StringReader stringReader = new StringReader();
	private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
	private final FusionLeaderReader fusionLeaderReader = new FusionLeaderReader();
	private final ResponseNonLeader responseNonLeader = new ResponseNonLeader();
	private final FileMessageReader fileMessageReader = new FileMessageReader();

	private final ConnexionReader connexionReader = new ConnexionReader();
	private final OpReader opReader = new OpReader();

	private boolean closed = false;

	private final String serverName;

	public ContextServer(Server server, SelectionKey key) {
		Objects.requireNonNull(server);
		Objects.requireNonNull(key);
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.server = server;
		this.serverName = server.getName();
	}

	public SelectionKey getKey() {
		return key;
	}

	/**
	 * Make a paquet with new leader information
	 * @param addressLeader InetSocketAddress
	 * @throws IOException
	 */
	@Override
	public void changeLeaderPaquet(InetSocketAddress addressLeader) throws IOException {
		Objects.requireNonNull(addressLeader);
		var message = new ChangeLeader(addressLeader);
		message.encode(bufferOut);
		doWrite();
	}

	/**
	 * Process the content of bufferIn
	 * <p>
	 * The convention is that bufferIn is in write-mode before the call to process
	 * and after the call
	 */
	private void processInMessage()  {
		for (;;) {
			Reader.ProcessStatus status = messageReader.process(bufferIn);

			switch (status) {
			case DONE:
				var value = messageReader.get();
				if (server.isLeader()) {
					server.broadcastServer(value, this);
				}
				if (value.nomServ().equals(server.getName()) && !server.isLeader()) {
					server.broadcastToLeader(value);
				}
				server.broadcast(value, this);
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
	 * Process the content of a private message
	 */
	private void processInPrivateMessage() {
		for (;;) {
			Reader.ProcessStatus status = privateMessageReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = privateMessageReader.get();
				server.sendPrivateMessage(value, key);
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
	 * Process the content of a connexion message
	 */
	private void processInConnexionClient() {
		Reader.ProcessStatus status = connexionReader.process(bufferIn);
		switch (status) {
			case DONE -> {
				var value = connexionReader.get();
				verifyConnection(value);
				connexionReader.reset();
			}
			case REFILL -> {}
			case ERROR -> silentlyClose();
		}
	}

	/**
	 * Process the content of a fusion demand of a non-leader
	 * @throws IOException
	 */
	private void processInDemandFusionFromNoLeader() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = responseNonLeader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = responseNonLeader.get();
				server.processAskFusion(value);
				// Nouvelle connexion avec le leader demandé
				responseNonLeader.reset();
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
	 * Process the content of a fusion demand of a leader
	 */
	private void processInDemandFusionFromLeader() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = fusionLeaderReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = fusionLeaderReader.get();
				if (server.isLeader()) {
					verifyServers(value);
				} else {
					sendResponseNotLeader();
				}
				fusionLeaderReader.reset();
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
	 * Process the content of a change of leadership
	 * @throws IOException
	 */
	private void processInChangeLeader() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = responseNonLeader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = responseNonLeader.get();
				System.out.println("LE NOUVEAU LEADER EST :" + value);
				server.resetLeader();
				server.processAskFusion(value);
				responseNonLeader.reset();
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
	 * Process the content of a change of leadership
	 * @throws IOException
	 */
	private void processInMerge() {
		for (;;) {
			Reader.ProcessStatus status = stringReader.process(bufferIn);
			switch (status) {
			case DONE:
				var value = stringReader.get();
				System.out.println("LE NOUVEAU LEADER EST :" + value);
				server.addContexteToLeader(this);
				stringReader.reset();
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
	 * Make a sendPaquet and send it
	 * @throws IOException
	 */
	private void sendConnect(boolean condition) {
		bufferOut.clear();
		if (condition) {
			var message = new SendConnect(server.getName());
			message.encode(bufferOut);
		} else
			bufferOut.put((byte) 8);
		updateInterestOps();
	}

	/**
	 * Make a paquet to respond to a merger request and send it
	 * @throws IOException
	 */
	private void sendRestponseFusion(boolean condition, DemandeFusionLeader messageFusionLeader) throws IOException {
		if (condition) {
			new ResponseFusionOk(server.getName(), server.getInetSocketAddress(), server.getServersNames()).encode(bufferOut);
			if (Objects.equals(server.verifyLeader(messageFusionLeader.name()), messageFusionLeader.name())) {
				server.setLeader(messageFusionLeader.addressEmetteur(), this);
				server.changeLeader();
			} else {
				server.registerServer(messageFusionLeader.name(), this);
				server.addContexteToLeader(this);
				server.addServersNames(messageFusionLeader);
			}
		} else
			bufferOut.put((byte) 10);
		updateInterestOps();
	}


	/**
	 * Make a paquet to respond to a merger request and send it
	 * @throws IOException
	 */
	private void sendResponseNotLeader() {
		var message = new ResponseNotLeader(server);
		message.encode(bufferOut);
		updateInterestOps();
	}

	private void verifyServers(DemandeFusionLeader messageFusionLeader) throws IOException {
		sendRestponseFusion(server.verifyServers(messageFusionLeader, this), messageFusionLeader);
	}

	/**
	 * Verify logins on the server
	 * @throws IOException
	 */
	private void verifyConnection(String login) {
		if (server.register(login, this)) {
			sendConnect(true);
		} else {
			sendConnect(false);
		}
	}

	/**
	 * Analysis of received packets
	 * @throws IOException
	 */
	private void processIn() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = opReader.process(bufferIn);
			switch (status) {
			case DONE:
				var opCode = opReader.get();
				switch (opCode) {
				case 0 -> processInConnexionClient();
				case 2 -> processInMessage();
				case 3 -> processInPrivateMessage();
				case 4 -> processInFileMessage();
				case 5 -> processInDemandFusionFromLeader();
				case 12 -> processInDemandFusionFromNoLeader();
				case 14 -> processInChangeLeader();
				case 15 -> processInMerge();
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
	 * File package process
	 */
	private void processInFileMessage() {
		for (;;) {
			Reader.ProcessStatus status = fileMessageReader.process(bufferIn);

			switch (status) {
			case DONE:
				var value = fileMessageReader.get();
				server.sendFileMessage(value, key);
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
	 * Add a message to the message queue, tries to fill bufferOut and
	 * updateInterestOps
	 *
	 * @param msg
	 */
	@Override
	public void queueMessage(Message msg) {
		Objects.requireNonNull(msg);
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

	@Override
	public String getServerName() {
		return serverName;
	}
}