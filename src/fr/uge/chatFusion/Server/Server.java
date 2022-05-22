package fr.uge.chatFusion.Server;

import fr.uge.chatFusion.Context.ContextServer;
import fr.uge.chatFusion.Context.ContextServerFusion;
import fr.uge.chatFusion.Context.InterfaceContexteServ;
import fr.uge.chatFusion.Utils.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
	private static final Logger logger = Logger.getLogger(Server.class.getName());

	private ContextServerFusion uniqueContext;

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final HashMap<String, ContextServer> clients;
	private final HashMap<String, InterfaceContexteServ> servers;
	private final Set<String> serversNames = new HashSet<>();
	private final Set<InterfaceContexteServ> serversContexte = new HashSet<>();
	private InetSocketAddress leader;
	private InterfaceContexteServ leaderContext;
	private final Thread console;
	private final ArrayDeque<String> queue = new ArrayDeque<>();
	private final String name;
	private final InetSocketAddress inetSocketAddress;

	public Server(int port, String name) throws IOException {
		Objects.requireNonNull(name);
		inetSocketAddress = new InetSocketAddress(port);
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(inetSocketAddress);
		selector = Selector.open();
		this.clients = new HashMap<>();
		this.leader = inetSocketAddress;
		this.servers = new HashMap<>();
		console = new Thread(this::consoleServerRun);
		console.setDaemon(true);
		this.name = name;
		// Si leader est lui-même le contexte leader est null
		leaderContext = null;
	}

	public boolean isLeader() {
		return leader.equals(inetSocketAddress);
	}

	private void consoleServerRun() {
		try (var scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine()) {
				var msg = scanner.nextLine();
				sendCommand(msg);
			}
		}
		logger.log(Level.INFO, "Console thread stopping");
	}

	private void sendCommand(String command) {
		synchronized (queue) {
			queue.addLast(command);
			selector.wakeup();
		}
	}

	private void askFusionToLeader(String ipAdress, int port) throws IOException {
		var demand = new DemandeFusionNoLeader(new InetSocketAddress(ipAdress, port));
		leaderContext.sendAskToLeader(demand);
	}

	public void askFusionToLeader(InetSocketAddress address) throws IOException {
		Objects.requireNonNull(address);
		var demand = new DemandeFusionNoLeader(address);
		leaderContext.sendAskToLeader(demand);
	}

	public void changeLeader() throws IOException {
		logger.info("CHANGEMENT DE LEADER");
		for (var client : this.serversContexte) {
			if (Objects.isNull(client))
				continue;
			client.changeLeaderPaquet(leader);
		}
	}

	private void processAskFusion(String ipAdress, int port) throws IOException {
		SocketChannel sc = SocketChannel.open();
		var newSc = new InetSocketAddress(ipAdress, port);
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new ContextServerFusion(key, logger, this);
		key.attach(uniqueContext);
		sc.connect(newSc);
	}

	public void processAskFusion(InetSocketAddress address) throws IOException {
		Objects.requireNonNull(address);
		SocketChannel sc = SocketChannel.open();
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new ContextServerFusion(key, logger, this);
		key.attach(uniqueContext);
		sc.connect(address);
	}

	private void processCommands() {
		synchronized (queue) {
			while (!queue.isEmpty()) {
				var msg = queue.poll();
				var tmp = msg.split(" ");
				if (tmp.length != 3 && tmp.length != 1) {
					logger.warning("Erreur de commande");
					break;
				}
				if (!"FUSION".equals(tmp[0]) && !"LEADER".equals(tmp[0])) {
					logger.warning("Commande pas supportée");
					break;
				}
				if ("LEADER".equals(tmp[0])) {
					logger.info("Mon leader est : " + leader + " " + isLeader() + " " + serversContexte.size());

				} else {
					logger.info("Tentative de Fusion");
					try {
						if (isLeader())
							processAskFusion(tmp[1], Integer.parseInt(tmp[2]));
						else {
							askFusionToLeader(tmp[1], Integer.parseInt(tmp[2]));
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		console.start();
		while (!Thread.interrupted()) {
//			Helpers.printKeys(selector); // for debug
//			Helpers.printKeys(selectorServers);
//			System.out.println("Starting select");
			try {
				// selectorServers.select(this::treatKeyClient);
				selector.select(this::treatKey);
				processCommands();
				updateClients();
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
//			System.out.println("Select finished");
		}
	}

	private void updateClients() {
//		for(var key : selector.keys()) {
//			if(!key.isValid()) {
//				clients.remove(key.channel());
//			}
//		}

		for (var entry : clients.entrySet()) {
			if (!entry.getValue().getKey().isValid()) {
				clients.remove(entry.getKey());
			}
		}
	}

	private void treatKey(SelectionKey key) {
//		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			if (key.isValid() && key.isConnectable()) {
				uniqueContext.doConnect(name, inetSocketAddress, servers);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			System.out.println("fin");
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((InterfaceContexteServ) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((InterfaceContexteServ) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			System.out.println("fin");
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null) {
			logger.warning("selector was wrong");
			return;
		}
		sc.configureBlocking(false);
		var key1 = sc.register(selector, SelectionKey.OP_READ);
		key1.attach(new ContextServer(this, key1));
	}

	public void silentlyClose(SelectionKey key) {
		Objects.requireNonNull(key);
		Channel sc = key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public void broadcastServer(MessagePublique msg, InterfaceContexteServ k) {
		Objects.requireNonNull(msg);
		Objects.requireNonNull(k);
		for (var server : serversContexte) {
			if (!Objects.isNull(server) && !server.equals(k)) {
				server.queueMessage(msg);
			}

		}
	}

	public void broadcast(MessagePublique msg, InterfaceContexteServ k) {
		Objects.requireNonNull(msg);
		Objects.requireNonNull(k);
		for (var client : this.clients.entrySet()) {
			var c = client.getValue();
			if (!Objects.isNull(c) && !c.equals(k)) {
				c.queueMessage(msg);
			}

		}
	}

	public boolean register(String login, ContextServer context) {
		Objects.requireNonNull(login);
		Objects.requireNonNull(context);
		if (clients.containsKey(login)) {
			return false;
		} else {
			clients.put(login, context);
			return true;
		}
	}

	public String verifyLeader(String nameEmetteur) {
		Objects.requireNonNull(nameEmetteur);
		System.out.println("LE NOUVEAU LEADER EST : " + (name.compareTo(nameEmetteur) < 0 ? name : nameEmetteur));
		return name.compareTo(nameEmetteur) < 0 ? name : nameEmetteur;
	}

	public boolean verifyServers(DemandeFusionLeader msg, ContextServer contextServer) {
		Objects.requireNonNull(msg);
		if (msg.servers().size() == 0)
			return true;
		for (var server : msg.servers()) {
			if (serversNames.contains(server))
				return false;
		}
		return true;
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 2) {
			usage();
			return;
		}
		new Server(Integer.parseInt(args[0]), args[1]).launch();
	}

	private static void usage() {
		System.out.println("Usage : Server port name");
	}

	public void sendPrivateMessage(MessagePrivate value, SelectionKey k) {
		Objects.requireNonNull(value);
		Objects.requireNonNull(k);
		if (value.servDest().equals(name)) {
			var context = clients.get(value.loginDest());
			if (context != null) {
				context.queueMessage(value);
			}
		} else {
			if (isLeader()) {
				for (var entry : servers.entrySet()) {
					if (value.servDest().equals(entry.getKey())) {
						entry.getValue().queueMessage(value);
					}
				}
			} else {
				if (leaderContext != null)
					leaderContext.queueMessage(value);

			}
		}
	}

	public void sendFileMessage(MessageFichier value, SelectionKey k) {
		Objects.requireNonNull(value);
		Objects.requireNonNull(k);
		if (value.servDest().equals(name)) {
			var context = clients.get(value.loginDest());
			if (context != null) {
				context.queueMessage(value);
			}
		} else {
			if (isLeader()) {
				for (var entry : servers.entrySet()) {
					if (value.servDest().equals(entry.getKey())) {
						entry.getValue().queueMessage(value);
					}
				}
			} else {
				if (leaderContext != null) {
					leaderContext.queueMessage(value);
				}
			}
		}
	}

	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}

	public void setLeader(InetSocketAddress leader, InterfaceContexteServ contextLeader) {
		Objects.requireNonNull(leader);
		Objects.requireNonNull(contextLeader);
		this.leader = leader;
		this.leaderContext = contextLeader;
	}

	public InetSocketAddress getLeader() {
		return leader;
	}

	public Set<String> getServersNames() {
		return serversNames;
	}

	public String getName() {
		return name;
	}

	public void addContexteToLeader(InterfaceContexteServ contexteServ) {
		Objects.requireNonNull(contexteServ);
		this.serversContexte.add(contexteServ);
	}

	public void broadcastToLeader(MessagePublique value) {
		Objects.requireNonNull(value);
		if (leaderContext != null) {
			leaderContext.queueMessage(value);
		}

	}

	public void addServersNames(DemandeFusionLeader messageFusionLeader) {
		Objects.requireNonNull(messageFusionLeader);
		serversNames.add(messageFusionLeader.login());
		serversNames.addAll(messageFusionLeader.servers());

	}

	public void registerServer(String login, InterfaceContexteServ contextServer) {
		Objects.requireNonNull(login);
		Objects.requireNonNull(contextServer);
		servers.put(login, contextServer);
	}

	public void resetLeader() {
		leader = inetSocketAddress;
	}
}
