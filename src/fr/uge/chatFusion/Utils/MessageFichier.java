package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record MessageFichier(String servEmetteur, String login, String servDest, String loginDest, String fileName,
		int nb_blocks, byte[] blocks) implements Message {

	@Override
	public String msg() {
		return null;
	}

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

		var fileNameEncode = StandardCharsets.UTF_8.encode(fileName);
		var fileNameSize = fileNameEncode.remaining();

		var size = sizeLogin + sizeServEmetteur + fileNameSize + sizeLoginDest + servSize + 4 * Integer.BYTES
				+ Byte.BYTES;

		if (size > 1024)
			throw new IllegalStateException();
		if (blocks.length > 5000) {
			return false;
		}

		if (size + blocks.length > bufferOut.remaining()) {
			return false;
		}


		bufferOut.put((byte) 4);

		bufferOut.putInt(sizeServEmetteur);
		bufferOut.put(servEmetteurEncode);

		bufferOut.putInt(sizeLogin);
		bufferOut.put(log);

		bufferOut.putInt(servSize);
		bufferOut.put(servDestEncode);

		bufferOut.putInt(sizeLoginDest);
		bufferOut.put(logDest);

		bufferOut.putInt(fileNameSize);
		bufferOut.put(fileNameEncode);

		bufferOut.putInt(nb_blocks);


		bufferOut.putInt(blocks.length);
		bufferOut.put(blocks);
		return true;

	}

}
