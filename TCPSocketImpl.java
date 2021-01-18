import java.util.Random;
import java.net.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class TCPSocketImpl extends TCPSocket {

    public enum CongestionPossibleStates {
        SLOW_START, CONGST_AVOIDANCE, NONE
    }

    public enum State {
        ESTABLISHED, SYN_SEND, NONE, FIN_WAIT_1, FIN_WAIT_2, TIME_WAIT,
    }

    public EnhancedDatagramSocket socket;
    private String IP;
    private int PORT;
    private int SequenceNumber = 0;
    private int AckNumber = 0;
    private int SERVER_PORT = 1239;
    private State STATE = State.NONE;
    private int HEADER_SIZE = 12;
    private int MSS = 1480 - HEADER_SIZE;
    private int NumBytes = MSS;
    private SenderGBN sGBN;

    private InetAddress ip_addr = InetAddress.getByName("localhost");
   

    public TCPSocketImpl(String ip, int port) throws Exception {
        super(ip, port);
        this.log("Constructor Called");
        System.out.println(port);
        this.IP = ip;
        this.PORT = port;
        this.socket= new EnhancedDatagramSocket(this.PORT);
        this.log("Socket Created");
    }

    public byte[] oneFIN(byte[] sendDataBytes) {
        sendDataBytes[9] = 0x01;
        return sendDataBytes;
    }

    public boolean isFIN(byte[] receivedDataBytes) {
        return ((receivedDataBytes[9] & 0x01) == 0x01);
    }


    public boolean isACK(byte[] receivedDataBytes) {
        return ((receivedDataBytes[9] & 0x10) == 0x10);
    }

    public int waitForACK() throws IOException {
        byte[] receivedDataBytes = new byte[NumBytes];
        DatagramPacket receivePacket = new DatagramPacket(receivedDataBytes, receivedDataBytes.length);
        while(!isACK(receivePacket.getData())) {
            receivedDataBytes = new byte[NumBytes];
            receivePacket = new DatagramPacket(receivedDataBytes, receivedDataBytes.length);
            this.socket.receive(receivePacket);
        }

        byte[] arr = Arrays.copyOfRange(receivePacket.getData(), 4, 7);
        ByteBuffer wrapped = ByteBuffer.wrap(arr);
        short ackNumber = wrapped.getShort();
        return ackNumber;
    }

    public void terminateConnection() throws IOException, InterruptedException {
        byte[] sendDataBytes = new byte[NumBytes];
        sendDataBytes = oneFIN(sendDataBytes);
        DatagramPacket sendPacket =  new DatagramPacket(sendDataBytes, sendDataBytes.length, ip_addr, SERVER_PORT);
        this.socket.send(sendPacket);
        this.log("FIN message sent");
        this.STATE = State.FIN_WAIT_1;

        waitForACK();
        this.log("ACK received. Waiting for server's FIN");
        this.STATE = State.FIN_WAIT_2;

        byte[] receivedDataBytes = new byte[NumBytes];
        DatagramPacket receivePacket = new DatagramPacket(receivedDataBytes, receivedDataBytes.length);
        while(isFIN(receivePacket.getData())) {
            receivedDataBytes = new byte[NumBytes];
            receivePacket = new DatagramPacket(receivedDataBytes, receivedDataBytes.length);
            this.socket.receive(receivePacket);    
        }
        this.STATE = State.TIME_WAIT;
        this.log("Server's FIN received. Waiting for 30 seconds and then closing the socket ...");

        TimeUnit.SECONDS.sleep(30);

        this.socket.close();
        this.log("Socket closed\nConnection terminated");
        this.STATE = State.NONE;
    }

    public void establishConnection(){

        try{
            byte[] sendDataBytes = new byte[NumBytes];
            String SequenceNumberString= Integer.toString(this.SequenceNumber);
            String AckNumberString = Integer.toString(this.AckNumber);
            String SendDataString="SYN" + " "+ SequenceNumberString + " " + AckNumberString;
            sendDataBytes = SendDataString.getBytes();
            this.log("Sending" + SendDataString);
            System.out.println("Here");
            DatagramPacket sendPacket = new DatagramPacket(sendDataBytes, sendDataBytes.length,ip_addr, SERVER_PORT);
            this.socket.send(sendPacket);
            this.STATE = State.SYN_SEND;
            this.log("SYN_SEND");

            boolean connection_completed = false;
            while(connection_completed == false){

                byte[] receivedData = new byte[NumBytes];
                DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
                this.socket.receive(receivedPacket);
                String ReceivedString = new String(receivedPacket.getData());
                this.log("Received_Data "+ ReceivedString);
                
                String[] received_splited = ReceivedString.split("\\s+");
                int receivedSeqNum = Integer.parseInt(received_splited[1].trim());
                int receivedAckNum = Integer.parseInt(received_splited[2].trim());

                if(this.STATE == State.SYN_SEND){
                    if(receivedAckNum == this.SequenceNumber + 1 && received_splited[0].equals("SYN-ACK")){

                        this.SequenceNumber = receivedAckNum;
                        this.AckNumber = receivedSeqNum + 1;
                        String SeqNumStr = Integer.toString(this.SequenceNumber);
                        String AckNumStr = Integer.toString(this.AckNumber);
                        String sendDataStr = "ACK" + " " + SeqNumStr +" "+ AckNumStr;
                        sendDataBytes = sendDataStr.getBytes();
                        
                        sendPacket = new DatagramPacket(sendDataBytes, sendDataBytes.length, ip_addr, this.SERVER_PORT);
                        this.socket.send(sendPacket);
                        
                        this.STATE = State.ESTABLISHED;
                        this.log("ESTABLISHED");
                        connection_completed = true;
                    }
                }
            }

            if(this.STATE == State.ESTABLISHED){

                return;
            }
        }catch(Exception ex){
            System.out.println("Exception");
        }
    }

    @Override
    public long getWindowSize() {
        return sGBN.getWindowSize();
    }

    @Override
    public long getSSThreshold() {
        return sGBN.getSSThreshold();
    }

    @Override
    public void send(String pathToFile) throws Exception {

        establishConnection();

        File sendFile = new File(pathToFile);
        Scanner reader = new Scanner(sendFile);
        byte[] msg = new byte[(short)sendFile.length()];
        int i = 0;
        for(i = 0; reader.hasNextByte(); i += 1) {
            msg[i] = reader.nextByte();
        }
        reader.close();

        sGBN = new SenderGBN(socket, ip_addr, SERVER_PORT);
        sGBN.transmit(msg);

        terminateConnection();
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        ReceiverGBN rGBN = new ReceiverGBN(socket);
        rGBN.receive(pathToFile);
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    public void log(String msg) {
        System.out.println(msg);
    }

}
