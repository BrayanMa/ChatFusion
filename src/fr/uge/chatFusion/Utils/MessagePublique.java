package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public record MessagePublique(String login, String msg) implements Message{
	
	@Override
	public void encode(ByteBuffer bufferOut) {
		
		var log = StandardCharsets.UTF_8.encode(login);
		var sizeLogin = log.remaining();
		var content = StandardCharsets.UTF_8.encode(msg);
		var sizeContent = content.remaining();
		var size = sizeLogin + sizeContent + 2 * Integer.BYTES + Byte.BYTES;

		if (size > 1024)
			throw new IllegalStateException();
		if (size > bufferOut.remaining()) {
			return;
		}
		bufferOut.put((byte)2);
		bufferOut.putInt(sizeLogin);
		bufferOut.put(log);
		bufferOut.putInt(sizeContent);
		bufferOut.put(content);
		
	}
}
