package fr.uge.chatFusion.Server;

import fr.uge.chatFusion.Context.ContextServer;
import fr.uge.chatFusion.Context.ContextServerFusion;
import fr.uge.chatFusion.Context.InterfaceContexteServ;
import fr.uge.chatFusion.Utils.MessageFusion;
import fr.uge.chatFusion.Utils.MessagePrivate;
import fr.uge.chatFusion.Utils.MessagePublique;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(Server.class.getName());

	private final SocketChannel sc;
	private ContextServerFusion uniqueContext;
	//private final Selector selectorServers;

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final HashMap<String, ContextServer> clients;
	private final HashMap<String, InetSocketAddress> servers;
	private InetSocketAddress leader;
	private ContextServerFusion leaderContext;
	private final Thread console;
	private final ArrayDeque<String> queue = new ArrayDeque<>();
	private final String name;
	private final InetSocketAddress inetSocketAddress ;

	public Server(int port, String name) throws IOException {
		inetSocketAddress = new InetSocketAddress(port);
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(inetSocketAddress);
		selector = Selector.open();
		//selectorServers = Selector.open();
		this.clients = new HashMap<>();
		this.leader =  inetSocketAddress;
		this.servers = new HashMap<>();
		console = new Thread(this::consoleServerRun);
		console.setDaemon(true);
		this.name = name;
		// Si leader est lui-même le contexte leader est null
		leaderContext = null;
		this.sc = SocketChannel.open();
		

	}
	
	public boolean isLeader() {
		return leader.equals(inetSocketAddress) ;
	}
	

	private void consoleServerRun(){
		try (var scanner = new Scanner(System.in)){
			while (scanner.hasNextLine()){
				var msg = scanner.nextLine();
				sendCommand(msg);
			}
		}
		logger.log(Level.INFO, "Console thread stopping");
	}

	private void sendCommand(String command){
		synchronized (queue){
			queue.addLast(command);
			selector.wakeup();
		}
	}

	private void processAskFusion(String ipAdress, int port) throws IOException {
		var newSc = new InetSocketAddress(ipAdress, port);
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new ContextServerFusion(key, logger, this);
		key.attach(uniqueContext);
		sc.connect(newSc);
	}

	private void transmitToLeader(String ipAdress, int port) throws IOException {
		var adr = new InetSocketAddress(ipAdress, port);
		leaderContext.makeConnectionPaquetToLeader(adr);
	}

	private void processCommands(){
		synchronized (queue){
			while (!queue.isEmpty()){
				var msg = queue.poll();
				var tmp = msg.split(" ");
				if(tmp.length != 3){
					logger.warning("Erreur de commande");
					break;
				}
				if (!"FUSION".equals(tmp[0])) {
					logger.warning("Commande pas supportée");
					break;
				}
				System.out.println("Tentative de Fusion");


				try {
					if(isLeader())
						processAskFusion(tmp[1], Integer.parseInt(tmp[2]));
					//else
					//	transmitToLeader(msg);
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		console.start();
		while (!Thread.interrupted()) {
			Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				//selectorServers.select(this::treatKeyClient);
				selector.select(this::treatKey);
				processCommands();
				updateClients();
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}

	private void updateClients() {
//		for(var key : selector.keys()) {
//			if(!key.isValid()) {
//				clients.remove(key.channel());
//			}
//		}
		
		for( var entry : clients.entrySet()) {
			if(!entry.getValue().getKey().isValid()) {
				clients.remove(entry.getKey());
			}
		}
	}

//	private void treatKeyClient(SelectionKey key) {
//		try {
//			if (key.isValid() && key.isConnectable()) {
//				uniqueContext.doConnect(name, servers);
//			}
//			if (key.isValid() && key.isWritable()) {
//				uniqueContext.doWrite();
//			}
//			if (key.isValid() && key.isReadable()) {
//				uniqueContext.doRead();
//			}
//		} catch (IOException ioe) {
//			// lambda call in select requires to tunnel IOException
//			throw new UncheckedIOException(ioe);
//		}
//	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			if (key.isValid() && key.isConnectable()) {
				uniqueContext.doConnect(name, inetSocketAddress ,servers);
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
		Channel sc = key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public void broadcast(MessagePublique msg, SelectionKey k) {
		for(var clients : this.clients.entrySet()) {
			var c = clients.getValue();
			if (Objects.isNull(c))
				continue;
			c.queueMessage(msg);
		}
		/*for (var key : selector.keys()) {
			// ATTENTION
			ContextServer c = (ContextServer) key.attachment();
			if (Objects.isNull(c))
				continue;
			if (key.isValid() && key != k) {
				c.queueMessage(msg);
			}
		}*/
	}

	public boolean register(String login, ContextServer context) {
		if (clients.containsKey(login)) {
			return false;
		} else {
			clients.put(login, context);
			return true;
		}
	}

	public boolean verifyServers(MessageFusion msg) {
		if(msg.nbrServ() == 0){
			servers.put(msg.login(), msg.addressEmetteur());
			System.out.println("servers : " + servers);
			for (var value : servers.entrySet()) {
				System.out.println(value.getKey() + " " + value.getValue());
			}
			return true;
		}
		for (var server : msg.servers().values()) {
			if(servers.containsValue(server)) {
				return false;
			}
		}
		servers.put(msg.login(), msg.addressEmetteur());
		servers.putAll(msg.servers());
		System.out.println("servers : " + servers);
		for (var value : servers.entrySet()) {
			System.out.println(value.getKey() + " " + value.getValue());
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
		
		var context = clients.get(value.loginDest());
		if(context != null) {
			context.queueMessage(value);
			return;
		}
		var c = (InterfaceContexteServ) k.attachment();
		c.sendUserNotFound(value);
		
//		for (var entry : clients.entrySet()) {
//			if (entry.getValue().equals(value.loginDest())) {
//				var key = entry.getKey().keyFor(selector);
//				ContextServer c = (ContextServer) key.attachment();
//				c.queueMessage(value);
//				return;
//			}
//		}
//		var c = (InterfaceContexteServ) k.attachment();
//		c.sendUserNotFound(value);
//		System.out.println("pas trouvé");
	}
}
