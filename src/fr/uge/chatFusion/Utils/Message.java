package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;

public interface Message {

	 String login();
	 String msg();
	 boolean encode(ByteBuffer bufferOut);
}
