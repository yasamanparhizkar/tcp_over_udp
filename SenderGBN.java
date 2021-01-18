import java.net.*;
import java.io.IOException;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class SenderGBN {

	public SenderGBN(DatagramSocket socket, InetAddress dip, int dport) {
		this.socket = socket;
		buffer = new byte[SIZE_OF_BUFFER];
		desIP = dip;
		desPort = dport;
		sendBase = 0;
		cwnd = MSS;
		nextSeqN = 0;
		bufferEnd = 0;

		state = CongestionPossibleStates.SLOW_START;
		SlowStartThreshold =  65536 ;

		timer = new MyTimer();
		receiver = new Receiver();
	}

	private class CouldNotCancelTimerTask extends Exception {}

	private class TooLongMessageException extends Exception {}

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

	private class MyTimer extends Timer {

		private class Retransmit extends TimerTask {
			public void run() {
				try { sender(); }
				catch(IOException e) {
					log("IOException thrown in timertask");
				}
			}
		}

		private TimerTask task = new Retransmit();

		public void start() {
			schedule(task, TIMEOUT_INTERVAL, TIMEOUT_INTERVAL);
		}

		public void restart() throws CouldNotCancelTimerTask {
			if(!task.cancel())
				throw new CouldNotCancelTimerTask();
			task = new Retransmit();
			start();
		}

		public void stop() {
			log("Timer stopped");
			task.cancel();
			cancel();	
		}
	}

	public void packetReceived(DatagramPacket receivePacket) throws IOException {
		byte[] ackNumber = Arrays.copyOfRange(receivePacket.getData(), 4, 7);
        ByteBuffer wrapped = ByteBuffer.wrap(ackNumber);
        sendBase = wrapped.getShort();

        duplicateAck(sendBase);
        if(tripleAck()) {
        	slowStartToCongestionAvoidance();
        	resetDuplicateAckCount();
        }
        lastAcked = sendBase;

        if(sendBase == bufferEnd)
        	close();
        else if(sendBase == nextSeqN) {
        	timer.stop();
        	sender();
        }
        else {
        	try {
        		timer.restart();
        	}
        	catch(CouldNotCancelTimerTask e) {
        		log("Exception \"CouldNotCancelTimerTask\" thrown");
        	}
        }

        if(state == CongestionPossibleStates.SLOW_START) {
        	slowStartAckEventSetter();
        	if(cwnd > getSSThreshold())
        		state = CongestionPossibleStates.CONGST_AVOIDANCE;
        }
       	else if(state == CongestionPossibleStates.CONGST_AVOIDANCE)
       		caAckEventSetter();

	}

	public void sender() throws IOException {
	    while((nextSeqN < (sendBase + cwnd)) && (nextSeqN < bufferEnd)) {
	        byte[] sendDataBytes = new byte[MSS];
	        System.arraycopy(buffer, nextSeqN, sendDataBytes, 0, MSS);
	        DatagramPacket sendPacket = new DatagramPacket(sendDataBytes, sendDataBytes.length, desIP, desPort);
	        socket.send(sendPacket);
	        if(nextSeqN == sendBase)
	        	timer.start();
	        nextSeqN += MSS;
	    }
	}

	public void messageHandler(byte[] msg) {
		byte[] header;
		int chunkSize = MSS - HEADER_SIZE;
		int seqN = 0;
		int bufSeqN = 0;

		for(seqN = 0; seqN < msg.length; seqN += chunkSize) {
			header = new byte[HEADER_SIZE];
			System.arraycopy(ByteBuffer.allocate(4).putInt(bufSeqN).array()
								, 0, header, 0, 4);

			System.arraycopy(header, 0, buffer, bufSeqN, HEADER_SIZE);
			System.arraycopy(msg, seqN, buffer, bufSeqN + HEADER_SIZE, chunkSize);
			bufSeqN += (HEADER_SIZE + chunkSize);
		}
		bufferEnd = bufSeqN;
	}

	public void transmit(byte[] msg) throws IOException, TooLongMessageException {
		if(msg.length > SIZE_OF_BUFFER)
			throw new TooLongMessageException();
		messageHandler(msg);
		receiver.start();
		sender();
	}

	public void close() {
		timer.stop();
		timer.purge();
		receiver.close();
	}

	private void log(String logData) {
		System.out.println("[SenderGBN] " + logData);
	}

	public void slowStartToCongestionAvoidance(){

            this.SlowStartThreshold = this.cwnd / 2;
            this.cwnd = 1;
    }

    public void slowStartAckEventSetter(){
        this.cwnd = this.cwnd * 2;
    }

    public void caAckEventSetter(){
        this.cwnd = this.cwnd + MSS*(MSS/cwnd);
    }

    private void duplicateAck(int ackNum){
        if(ackNum == this.lastAcked)
            DuplicateAckCount++;
    }

    private boolean tripleAck(){
        if(DuplicateAckCount >= 3)
            return true;
        return false;
    }

    private void resetDuplicateAckCount(){
        this.DuplicateAckCount = 0;
    }

    public long getSSThreshold() {
        return this.SlowStartThreshold;
    }

    public long getWindowSize() {
        return this.cwnd;
    }

	public enum CongestionPossibleStates {
        SLOW_START, CONGST_AVOIDANCE, NONE
    }

	private int MSS = 1480;
	private int SIZE_OF_BUFFER = 20000;
	private long TIMEOUT_INTERVAL = 1000;
	private int HEADER_SIZE = 12;

	private byte[] buffer;
	private int sendBase;
	private int cwnd;
	private int nextSeqN;
	private int bufferEnd;
	private DatagramSocket socket;
	private MyTimer timer;
	private Receiver receiver;

	private CongestionPossibleStates state =
			CongestionPossibleStates.NONE;
    private int SlowStartThreshold = 0;
    private int lastAcked = 0;
    private int DuplicateAckCount = 0;

	private InetAddress desIP;
	private int desPort;


}