package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.Message.FileMessageReader;
import fr.uge.chatFusion.Reader.Message.FusionLeaderReader;
import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.OpReader;
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
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContextServerFusion implements InterfaceContexteServ {
	static private final int BUFFER_SIZE = 10_000;
	private final Logger logger;

	private final SelectionKey key;
	private final SocketChannel sc;
	private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
	private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
	private final MessageReader messageReader = new MessageReader();
	private final PrivateMessageReader privateMessageReader = new PrivateMessageReader();
	private final FusionLeaderReader responseFusionReader = new FusionLeaderReader();
	private final ResponseNonLeader responseNonLeader = new ResponseNonLeader();
	private final FileMessageReader fileMessageReader = new FileMessageReader();

	private final Server server;
	private final OpReader opReader = new OpReader();

	private boolean closed = false;
	
	private final String serverName;


	public ContextServerFusion(SelectionKey key, Logger logger, Server server) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(logger);
		Objects.requireNonNull(server);
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.logger = logger;
		this.server = server;
		this.serverName = server.getName();
	}

	/**
	 * Create connexion paquet to initiate the connexion with a server
	 * @param nameServ
	 * @param address
	 * @param servers
	 * @throws IOException
	 */
	@Override
	 public void makeConnectionPaquet(String nameServ, InetSocketAddress address,HashMap<String, InterfaceContexteServ> servers) throws IOException {
		Objects.requireNonNull(nameServ);
		Objects.requireNonNull(address);
		Objects.requireNonNull(servers);
		var message = new DemandeFusionLeader(nameServ,address, servers.keySet());
		message.encode(bufferOut);
		doWrite();
	}

	/**
	 * Create connexion paquet to finalize the merge with a server
	 * @param nameServ
	 * @throws IOException
	 */
	public void makeMergePaquet(String nameServ) throws IOException {
		Objects.requireNonNull(nameServ);
		var message = new Merge(nameServ);
		message.encode(bufferOut);
		doWrite();
	}

	/**
	 * Create a paquet to notify the new leader
	 * @param addressLeader
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
	 * Make the paquet and send it to ask a fusion
	 * @param demandeFusionNoLeader
	 * @throws IOException
	 */
	@Override
	public void sendAskToLeader(DemandeFusionNoLeader demandeFusionNoLeader) throws IOException {
		Objects.requireNonNull(demandeFusionNoLeader);
		demandeFusionNoLeader.encode(bufferOut);
		doWrite();
	}

	/**
	 * 	 Process the content of response to a fusion
	 * @throws IOException
	 */
	private void processInResponseFusion() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = responseFusionReader.process(bufferIn);
			switch (status) {
				case DONE:
					var value = responseFusionReader.get();
					if (Objects.equals(server.verifyLeader(value.name()), value.name())){
						server.setLeader(value.addressEmetteur(), this);
						server.changeLeader();
					}
					else{
						server.registerServer(value.name(), this);
						server.addContexteToLeader(this);
						server.addServersNames(value);
					}
					responseFusionReader.reset();
					return;
				case REFILL:
					return;
				case ERROR:
					logger.severe("Error in response fusion");
					silentlyClose();
					return;
			}
		}
	}

	/**
	 * Process the content of a fusion with a non-leader
	 * @throws IOException
	 */
	private void processInNonLeaderResponse() throws IOException{
		for (;;) {
			Reader.ProcessStatus status = responseNonLeader.process(bufferIn);
			switch (status) {
				case DONE:
					var value = responseNonLeader.get();
					server.processAskFusion(value);
					responseFusionReader.reset();
					return;
				case REFILL:
					return;
				case ERROR:
					logger.severe("Error in response fusion");
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
	private void processInMessage() {
		for (;;) {
			Reader.ProcessStatus status = messageReader.process(bufferIn);

			switch (status) {
			case DONE:
				var value = messageReader.get();
				
				if(server.isLeader()){
					server.broadcastServer(value, this);
				}
				if(value.nomServ().equals(server.getName()) && !server.isLeader()){
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
	 * Process the content of a ChangeLeader paquet
	 * @throws IOException
	 */
	private void processInChangeLeader() throws IOException {
		for (;;) {
			Reader.ProcessStatus status = responseNonLeader.process(bufferIn);
			switch (status) {
				case DONE:
					var value = responseNonLeader.get();
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
						case 2 -> processInMessage();
						case 3 -> processInPrivateMessage();
						case 4 -> processInFileMessage();
						case 9 -> {
							logger.info("Fusion établie");
							processInResponseFusion();
						}
						case 10 -> {
							logger.warning("Fusion refusée");
							silentlyClose();
						}
						case 11 -> {
							logger.info("Réception d'un paquet non leader");
							processInNonLeaderResponse();
						}
						case 14 -> {
							processInChangeLeader();
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
	 * Private message process
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
	 * Sending process
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
		if (sc.read(bufferIn) == -1)
			closed = true;
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



	public void doConnect(String nameServ, InetSocketAddress address,HashMap<String, InterfaceContexteServ> servers) throws IOException {
		Objects.requireNonNull(nameServ);
		Objects.requireNonNull(address);
		Objects.requireNonNull(servers);
		if (!sc.finishConnect()) {
			return;
		}
		logger.log(Level.INFO, "Tentative de connection au serveur...");

		if(server.isLeader()){
			makeConnectionPaquet(nameServ, address, servers);
		}else{
			makeMergePaquet(nameServ);
		}
		//makeConnectionPaquet(nameServ, servers);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	public void sendBuffer(ByteBuffer buffer) throws IOException {
		Objects.requireNonNull(buffer);
		bufferOut.put(buffer);
		doWrite();
	}

	public SelectionKey getKey() {
		return key;
	}

	@Override
	public void queueMessage(Message msg) {
		Objects.requireNonNull(messageReader);
		var buffer = ByteBuffer.allocate(BUFFER_SIZE);
		msg.encode(buffer);
		buffer.flip();

		queue.offer(buffer);
		processOut();
		updateInterestOps();
	}

	@Override
	public String getServerName() {
		return serverName;
	}
}
