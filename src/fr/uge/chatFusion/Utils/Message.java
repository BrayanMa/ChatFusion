package fr.uge.chatFusion.Utils;

import java.nio.ByteBuffer;

public interface Message {

	public String login();
	public String msg();
	public void encode(ByteBuffer bufferOut);
}
