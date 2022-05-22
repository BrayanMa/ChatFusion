package fr.uge.chatFusion.Reader.Message;

import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv4;
import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv6;
import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Utils.DemandeFusionLeader;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class FusionLeaderReader  implements Reader<DemandeFusionLeader> {
    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private final InetSocketAddressReaderv4 inetv4Reader = new InetSocketAddressReaderv4();
    private final InetSocketAddressReaderv6 inetv6Reader = new InetSocketAddressReaderv6();

    private String nomServ;
    private int nbServ;
    private InetSocketAddress serveurEmetteur;

    private DemandeFusionLeader message;

    @Override
    public Reader.ProcessStatus process(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        Set<String> servers = new HashSet<>();
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        var loginState = stringReader.process(buffer);
        if (loginState != Reader.ProcessStatus.DONE) {
            return loginState;
        }
        nomServ = stringReader.get();

        stringReader.reset();

        var sizeEmetteurState = intReader.process(buffer);
        if(sizeEmetteurState != Reader.ProcessStatus.DONE) {
            return sizeEmetteurState;
        }
        var sizeEmetteur = intReader.get();
        intReader.reset();
        if(sizeEmetteur == 4) {
            var EmetteurAdrState = inetv4Reader.process(buffer);
            if(EmetteurAdrState != Reader.ProcessStatus.DONE) {
                return EmetteurAdrState;
            }
            serveurEmetteur = inetv4Reader.get();
            inetv4Reader.reset();
        }

        var nbServState = intReader.process(buffer);
        if (nbServState != Reader.ProcessStatus.DONE) {
            return nbServState;
        }
        nbServ = intReader.get();
        intReader.reset();


        for (var i = nbServ; i > 0; i--) {
            var texteState = stringReader.process(buffer);
            if (texteState != Reader.ProcessStatus.DONE) {
                return texteState;
            }
            var nomServ1 = stringReader.get();
            stringReader.reset();

            servers.add(nomServ1);

        }

        state = State.DONE;

        message = new DemandeFusionLeader(nomServ, serveurEmetteur, servers);
        return Reader.ProcessStatus.DONE;
    }

    @Override
    public DemandeFusionLeader get() {
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
