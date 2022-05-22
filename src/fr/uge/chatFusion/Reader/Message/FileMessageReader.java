package fr.uge.chatFusion.Reader.Message;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.uge.chatFusion.Reader.Reader;
import fr.uge.chatFusion.Reader.State;
import fr.uge.chatFusion.Reader.Primitive.BlockReader;
import fr.uge.chatFusion.Reader.Primitive.IntReader;
import fr.uge.chatFusion.Reader.Primitive.StringReader;
import fr.uge.chatFusion.Utils.MessageFichier;

public class FileMessageReader implements Reader<MessageFichier> {

	private State state = State.WAITING;
	private final StringReader stringReader = new StringReader();
	private final IntReader intReader = new IntReader();
	private final BlockReader blockReader = new BlockReader();

	
	
	private String loginDest;
	private String servDest;
	private String servEmetteur;
	private String login;
	private String fileName;
	private int nbBlocks;
	private ByteBuffer block;
	
	private MessageFichier messageFichier;

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		
		var servEmetteurState = stringReader.process(bb);
		if (servEmetteurState != ProcessStatus.DONE) {
			return servEmetteurState;
		}
		servEmetteur = stringReader.get();
		stringReader.reset();
		

		var loginState = stringReader.process(bb);
		if (loginState != ProcessStatus.DONE) {
			return loginState;
		}
		login = stringReader.get();
		stringReader.reset();


		var servDestState = stringReader.process(bb);
		if (servDestState != ProcessStatus.DONE) {
			return servDestState;
		}
		servDest = stringReader.get();
		stringReader.reset();
		

		var loginDestState = stringReader.process(bb);
		if (loginDestState != ProcessStatus.DONE) {
			return loginDestState;
		}
		loginDest = stringReader.get();
		stringReader.reset();


		
		
		var fileNameState = stringReader.process(bb);
		if (fileNameState != ProcessStatus.DONE) {
			return fileNameState;
		}
		fileName = stringReader.get();
		stringReader.reset();
		

		var nbBlockState = intReader.process(bb);
		if (nbBlockState != ProcessStatus.DONE) {
			return nbBlockState;
		}
		nbBlocks= intReader.get();
		intReader.reset();
		

		var blockState = blockReader.process(bb);
		if (blockState != ProcessStatus.DONE) {
			return blockState;
		}
		block= blockReader.get();
		
		
		var blockarray = new byte[block.remaining()];
		var size= block.remaining();
		
		for(var i =0; i<size ; i++) { // transform bytebuffer to byte[]
			blockarray[i]=block.get();
		}
		

		
		state = State.DONE;

		
		
		messageFichier = new MessageFichier(servEmetteur, login, servDest, loginDest,fileName,nbBlocks, blockarray);
		

		return ProcessStatus.DONE;
	}

	@Override
	public MessageFichier get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return messageFichier;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		stringReader.reset();
		intReader.reset();
		blockReader.reset();
	}

}
