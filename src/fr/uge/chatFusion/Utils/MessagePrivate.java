package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MessagePrivate(String servEmetteur, String login, String servDest,String loginDest,  String msg) implements Message {

	@Override
	public boolean encode(ByteBuffer bufferOut) {
		Objects.requireNonNull(bufferOut);
		var log = StandardCharsets.UTF_8.encode(login);
		var sizeLogin = log.remaining();
		
		
		var servEmetteurEncode = StandardCharsets.UTF_8.encode(servEmetteur);
		var sizeServEmetteur = servEmetteurEncode.remaining();
		

		var logDest = StandardCharsets.UTF_8.encode(loginDest);
		var sizeLoginDest = logDest.remaining();

		var servDestEncode = StandardCharsets.UTF_8.encode(servDest);
		var servSize = servDestEncode.remaining();
		
		var content = StandardCharsets.UTF_8.encode(msg);
		var sizeContent = content.remaining();

		var size = sizeLogin +sizeServEmetteur +sizeContent + sizeLoginDest + servSize + 4 * Integer.BYTES + Byte.BYTES;

		if (size > 1024)
			throw new IllegalStateException();
		if (size > bufferOut.remaining()) {
			return false;
		}
		bufferOut.put((byte) 3);
		
		bufferOut.putInt(sizeServEmetteur);
		bufferOut.put(servEmetteurEncode);
		
		
		bufferOut.putInt(sizeLogin);
		bufferOut.put(log);
		

		bufferOut.putInt(servSize);
		bufferOut.put(servDestEncode);
		
		
		bufferOut.putInt(sizeLoginDest);
		bufferOut.put(logDest);
		
		
		bufferOut.putInt(sizeContent);
		bufferOut.put(content);
		return true;

	}

	// A METTRE DANS UNE AUTRE CLASSE
	public ByteBuffer createBufferUserNotFound() { 
		var log = StandardCharsets.UTF_8.encode(login);
		var sizeLogin = log.remaining();

		var dest = StandardCharsets.UTF_8.encode(loginDest);
		var sizeDest = dest.remaining();

		var buffer = ByteBuffer.allocate(1 + 2 * Integer.BYTES + sizeDest + sizeLogin);
		buffer.put((byte) 11).putInt(sizeLogin).put(log).putInt(sizeDest).put(dest);

		buffer.flip();
		return buffer;
	}
}
