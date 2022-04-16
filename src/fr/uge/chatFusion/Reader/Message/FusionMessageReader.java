package fr.uge.chatFusion.Reader.Message;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv4;
import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv6;
import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.Reader.ProcessStatus;
import fr.uge.chatFusion.Utils.MessageFusion;
import fr.uge.chatFusion.Utils.MessagePublique;

public class FusionMessageReader implements Reader<MessageFusion> {
	private State state = State.WAITING;
	private final StringReader stringReader = new StringReader();
	private final IntReader intReader = new IntReader();
	private final InetSocketAddressReaderv4 inetv4Reader = new InetSocketAddressReaderv4();
	private final InetSocketAddressReaderv6 inetv6Reader = new InetSocketAddressReaderv6();

	private String nomServ;
	private int nbServ;
	private InetSocketAddress serveurEmetteur;

	private HashMap<String, InetSocketAddress> servers;

	private MessageFusion message;

	@Override
	public ProcessStatus process(ByteBuffer buffer) {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		var loginState = stringReader.process(buffer);
		if (loginState != ProcessStatus.DONE) {
			return loginState;
		}
		nomServ = stringReader.get();
		stringReader.reset();

		var sizeEmetteurState = intReader.process(buffer);
		if(sizeEmetteurState != ProcessStatus.DONE) {
			return sizeEmetteurState;
		}
		var sizeEmetteur = intReader.get();
		intReader.reset();
		if(sizeEmetteur == 4) {
			var EmetteurAdrState = inetv4Reader.process(buffer);
			if(EmetteurAdrState != ProcessStatus.DONE) {
				return EmetteurAdrState;
			}
			serveurEmetteur = inetv4Reader.get();
		}

		var nbServState = intReader.process(buffer);
		if (nbServState != ProcessStatus.DONE) {
			return nbServState;
		}
		nbServ = intReader.get();
		intReader.reset();


		System.out.println("NB SSEEERV : " + nbServ);
		for (var i = nbServ; i > 0; i--) {
			var texteState = stringReader.process(buffer);
			if (texteState != ProcessStatus.DONE) {
				return texteState;
			}
			var nomServ1 = stringReader.get();
			stringReader.reset();

			var ipState = intReader.process(buffer);
			if (ipState != ProcessStatus.DONE) {
				return ipState;
			}
			var ipV = intReader.get();
			intReader.reset();

			if (ipV == 4) {
				var ipVstate = inetv4Reader.process(buffer);
				if (ipVstate != ProcessStatus.DONE) {
					return ipVstate;
				}
				var inetServ = inetv4Reader.get();
				inetv4Reader.reset();

				servers.put(nomServ1, inetServ);
			} else if (ipV == 6) {
				var ipVstate = inetv6Reader.process(buffer);
				if (ipVstate != ProcessStatus.DONE) {
					return ipVstate;
				}
				var inetServ = inetv6Reader.get();
				inetv6Reader.reset();
				servers.put(nomServ1, inetServ);
			} else {
				return ProcessStatus.ERROR;
			}

		}

		state = State.DONE;
		System.out.println("Emetteur : " + serveurEmetteur);

		message = new MessageFusion(nomServ, serveurEmetteur, nbServ, servers);
		return ProcessStatus.DONE;
	}

	@Override
	public MessageFusion get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return message;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		stringReader.reset();
	}
}
