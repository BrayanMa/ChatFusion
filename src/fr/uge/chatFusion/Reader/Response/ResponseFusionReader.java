package fr.uge.chatFusion.Reader.Response;

import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv4;
import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv6;
import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Utils.ResponseFusion;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ResponseFusionReader implements Reader<ResponseFusion> {
    private State state = State.WAITING;
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private final InetSocketAddressReaderv4 inetv4Reader = new InetSocketAddressReaderv4();
    private final InetSocketAddressReaderv6 inetv6Reader = new InetSocketAddressReaderv6();

    private InetSocketAddress adressLeader;
    private Map<String, InetSocketAddress> servers = new HashMap<>();
    private ResponseFusion responseFusion;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        var addressLeaderStatus = intReader.process(bb);
        if (addressLeaderStatus != ProcessStatus.DONE) {
            return addressLeaderStatus;
        }
        var ipVLeader = intReader.get();
        intReader.reset();

        if (ipVLeader == 4) {
            var addressLeaderStatusv4 = inetv4Reader.process(bb);
            if (addressLeaderStatusv4 != ProcessStatus.DONE) {
                return addressLeaderStatusv4;
            }
            adressLeader = inetv4Reader.get();
            inetv4Reader.reset();
            adressLeader = new InetSocketAddress(adressLeader.getAddress(), adressLeader.getPort());
        } else if (ipVLeader == 6) {
            var addressLeaderStatusv6 = inetv6Reader.process(bb);
            if (addressLeaderStatusv6 != ProcessStatus.DONE) {
                return addressLeaderStatusv6;
            }
            adressLeader = inetv6Reader.get();
            inetv6Reader.reset();
            adressLeader = new InetSocketAddress(adressLeader.getAddress(), adressLeader.getPort());
        }  else
            return ProcessStatus.ERROR;

        var nbServerState = intReader.process(bb);
        if (nbServerState != ProcessStatus.DONE) {
            return nbServerState;
        }
        var nbServer = intReader.get();
        intReader.reset();
        for (int i = nbServer; i > 0 ; i--) {
            var texteState = stringReader.process(bb);
            if (texteState != ProcessStatus.DONE) {
                return texteState;
            }
            var nomServ1 = stringReader.get();
            stringReader.reset();

            var ipState = intReader.process(bb);
            if (ipState != ProcessStatus.DONE) {
                return ipState;
            }
            var ipV = intReader.get();
            intReader.reset();

            if (ipV == 4) {
                var ipVstate = inetv4Reader.process(bb);
                if (ipVstate != ProcessStatus.DONE) {
                    return ipVstate;
                }
                var inetServ = inetv4Reader.get();
                inetv4Reader.reset();

                servers.put(nomServ1, inetServ);
            } else if (ipV == 6) {
                var ipVstate = inetv6Reader.process(bb);
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
        responseFusion = new ResponseFusion(adressLeader, servers);
        return ProcessStatus.DONE;
    }

    @Override
    public ResponseFusion get() {
        return responseFusion;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        stringReader.reset();
        intReader.reset();
    }
}
