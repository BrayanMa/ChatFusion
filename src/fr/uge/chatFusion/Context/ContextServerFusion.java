package fr.uge.chatFusion.Context;

import fr.uge.chatFusion.Reader.Message.MessageReader;
import fr.uge.chatFusion.Reader.Message.PrivateMessageReader;
import fr.uge.chatFusion.Reader.OpReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.Response.ResponseFusionReader;
import fr.uge.chatFusion.Server.Server;
import fr.uge.chatFusion.Utils.Message;
import fr.uge.chatFusion.Utils.MessageFusion;
import fr.uge.chatFusion.Utils.MessageFusionTransitToLeader;
import fr.uge.chatFusion.Utils.ResponseFusion;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
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
	private final ResponseFusionReader responseFusionReader = new ResponseFusionReader();
	private final Server server;
	private final OpReader opReader = new OpReader();

	private boolean closed = false;

	public ContextServerFusion(SelectionKey key, Logger logger, Server server) {
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.logger = logger;
		this.server = server;
	}

	private void makeConnectionPaquet(String nameServ, InetSocketAddress address,HashMap<String, InetSocketAddress> servers) throws IOException {
		var message = new MessageFusion(nameServ,address,servers.size(),servers);
		message.encode(bufferOut);
		doWrite();
	}

	public void makeConnectionPaquetToLeader(InetSocketAddress adr) throws IOException {
		var message = new MessageFusionTransitToLeader(adr);
		message.encode(bufferOut);
		doWrite();
	}

	private void processInResponseFusion() {
		for (;;) {
			Reader.ProcessStatus status = responseFusionReader.process(bufferIn);
			switch (status) {
				case DONE:
					var value = responseFusionReader.get();
					System.out.println("LEADER : " + value.leader());
					responseFusionReader.reset();
					break;
				case REFILL:
					return;
				case ERROR:
					System.out.println("ERREURvqfvqvdfvqd");
					silentlyClose();
					return;
			}
		}
	}
	private void processIn() {
		for (;;) {
			Reader.ProcessStatus status = opReader.process(bufferIn);
			switch (status) {
				case DONE:
					var opCode = opReader.get();
					switch (opCode) {
						case 9 -> {logger.info("Fusion établie");
									processInResponseFusion();

							}
						case 10 -> logger.warning("Fusion refusée");
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

	public void doConnect(String nameServ, InetSocketAddress address,HashMap<String, InetSocketAddress> servers) throws IOException {
		if (!sc.finishConnect()) {
			return;
		}
		logger.log(Level.INFO, "Tentative de connection au serveur...");

		if(server.isLeader()){
			makeConnectionPaquet(nameServ, address,servers);
		}else{

		}
		//makeConnectionPaquet(nameServ, servers);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	public SelectionKey getKey() {
		return key;
	}
}
