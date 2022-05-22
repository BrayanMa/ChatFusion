package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


public record MessagePublique(String login, String msg,String nomServ) implements Message{
	
	@Override
	public boolean encode(ByteBuffer bufferOut) {
		Objects.requireNonNull(bufferOut);
		var log = StandardCharsets.UTF_8.encode(login);
		var sizeLogin = log.remaining();
		
		var nomServEncode = StandardCharsets.UTF_8.encode(nomServ);
		var nomServSize = nomServEncode.remaining();
		
		var content = StandardCharsets.UTF_8.encode(msg);
		var sizeContent = content.remaining();
		
		
		
		var size = sizeLogin + sizeContent + nomServSize + 2 * Integer.BYTES + Byte.BYTES;

		if (size > 1024)
			throw new IllegalStateException();
		if (size > bufferOut.remaining()) {
			return false;
		}
		bufferOut.put((byte)2);
		bufferOut.putInt(sizeLogin);
		bufferOut.put(log);
		bufferOut.putInt(nomServSize);
		bufferOut.put(nomServEncode);
		bufferOut.putInt(sizeContent);
		bufferOut.put(content);
		return true;
		
	}
}
