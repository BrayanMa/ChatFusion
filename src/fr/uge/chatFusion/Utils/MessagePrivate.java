package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record MessagePrivate(String loginDest, String serv, String login, String msg) implements Message{

	

	@Override
	public void encode(ByteBuffer bufferOut) {
		var log = StandardCharsets.UTF_8.encode(login);
		var sizeLogin = log.remaining();
		var content = StandardCharsets.UTF_8.encode(msg);
		var sizeContent = content.remaining();
		
		var logDest = StandardCharsets.UTF_8.encode(loginDest);
		var sizeLoginDest = logDest.remaining();
		
		var servDest = StandardCharsets.UTF_8.encode(serv);
		var servSize = servDest.remaining();
		
		var size = sizeLogin + sizeContent + sizeLoginDest + servSize + 4 * Integer.BYTES +Byte.BYTES;

		if (size > 1024)
			throw new IllegalStateException();
		if (size > bufferOut.remaining()) {
			return;
		}
		bufferOut.put((byte)3);
		bufferOut.putInt(sizeLoginDest);
		bufferOut.put(logDest);
		bufferOut.putInt(servSize);
		bufferOut.put(servDest);
		bufferOut.putInt(sizeLogin);
		bufferOut.put(log);
		bufferOut.putInt(sizeContent);
		bufferOut.put(content);
		
	}
}
