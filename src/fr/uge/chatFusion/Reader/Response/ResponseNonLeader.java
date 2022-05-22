package fr.uge.chatFusion.Reader.Response;

import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv4;
import fr.uge.chatFusion.Reader.Primitive.InetSocketAddressReaderv6;
import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ResponseNonLeader implements Reader<InetSocketAddress> {
    private State state = State.WAITING;
    private final IntReader intReader = new IntReader();

    private final InetSocketAddressReaderv4 inetv4Reader = new InetSocketAddressReaderv4();
    private final InetSocketAddressReaderv6 inetv6Reader = new InetSocketAddressReaderv6();


    private InetSocketAddress serveurLeader;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        Objects.requireNonNull(bb);
        var sizeLeaderState = intReader.process(bb);
        if(sizeLeaderState != Reader.ProcessStatus.DONE) {
            return sizeLeaderState;
        }
        var sizeLeader = intReader.get();
        intReader.reset();
        if(sizeLeader == 4) {
            var LeaderAdrState = inetv4Reader.process(bb);
            if(LeaderAdrState != Reader.ProcessStatus.DONE) {
                return LeaderAdrState;
            }
            serveurLeader = inetv4Reader.get();
            inetv4Reader.reset();
            state = State.DONE;
        }
        return Reader.ProcessStatus.DONE;
    }

    @Override
    public InetSocketAddress get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return serveurLeader;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        intReader.reset();
        inetv4Reader.reset();
        inetv6Reader.reset();
    }
}
