import java.net.*;
import java.io.IOException;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.io.FileWriter;

public class ReceiverGBN {

	public enum State {
        ESTABLISHED, LAST_ACK, CLOSE_WAIT, NONE
    }

	public ReceiverGBN(DatagramSocket socket) {
		this.socket = socket;
		nextSeqN = 0;
		receiver = new Receiver();
		state = State.ESTABLISHED;
	}

	public boolean isFIN(byte[] receivedDataBytes) {
        return ((receivedDataBytes[9] & 0x01) == 0x01);
    }

    public boolean isACK(byte[] receivedDataBytes) {
        return ((receivedDataBytes[9] & 0x10) == 0x10);
    }

	private class Receiver extends Thread {
		boolean running = true;	

		public void run() {
			try {
				while(running) {
					byte[] receiveDataBytes = new byte[MSS];
					DatagramPacket receivePacket = new DatagramPacket(receiveDataBytes, receiveDataBytes.length);
					socket.receive(receivePacket);
					packetReceived(receivePacket);				
				}
			}
			catch(IOException e)
			{
				log("IOException caught in receiver");
			}
		}

		public void close() {
			running = false;
		}
	}

	public void packetReceived(DatagramPacket receivePacket) throws IOException {

		if(state == State.LAST_ACK) {
			if(isACK(receivePacket.getData())) {
				receiver.close();
			}
		}
		else {

			byte[] seqNumber = Arrays.copyOfRange(receivePacket.getData(), 0, 3);
	        ByteBuffer wrapped = ByteBuffer.wrap(seqNumber);

	        if(nextSeqN == wrapped.getShort()) {
	        	upperLayer(Arrays.copyOfRange(receivePacket.getData(), HEADER_SIZE,
	        									 receivePacket.getData().length));
	        	nextSeqN += MSS;

	        	byte[] ackMsg = new byte[MSS];
	        	ackMsg[9] = 0x10;
	        	if(isFIN(receivePacket.getData())) {
	        		ackMsg[9] = 0x11;
	        		state = State.CLOSE_WAIT;
	        	}

				System.arraycopy(ByteBuffer.allocate(4).putInt(nextSeqN).array(), 0, ackMsg, 4, 4);
	        	ackPacket = new DatagramPacket(ackMsg, ackMsg.length, receivePacket.getAddress(), receivePacket.getPort());
	        	socket.send(ackPacket);

	        	if(isFIN(receivePacket.getData()))
	        		state = State.LAST_ACK;
	        }
	        else {
	        	socket.send(ackPacket);
	        }
		}
	}

	public void upperLayer(byte[] data) throws IOException {
		Path path = Paths.get(pathToFile);
		Files.write(path, data, StandardOpenOption.APPEND);
	}

	public void receive(String pToFile) {
		this.pathToFile = pToFile;
		receiver.start();
	}

	public void close() {
		receiver.close();
	}

	private void log(String logData) {
		System.out.println("[ReceiverGBN] " + logData);
	}

	private int MSS = 1480;
	private int SIZE_OF_BUFFER = 20000;
	private long TIMEOUT_INTERVAL = 1000;
	private int HEADER_SIZE = 12;

	private int nextSeqN;
	private String pathToFile;
	private DatagramSocket socket;
	private Receiver receiver;
	private DatagramPacket ackPacket;
	private State state;

}