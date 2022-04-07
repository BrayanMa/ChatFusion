package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;

public interface Message {

	 String login();
	 String msg();
	 void encode(ByteBuffer bufferOut);
}
