import java.net.*;

public class TCPServerSocketImpl extends TCPServerSocket {

    public EnhancedDatagramSocket socket;
    private static int AckNumber = 0;
    private static int SequenceNumber = 0;
    private static int PORT;
    private int NumBytes = 1024;
    private static State STATE = State.NONE;
    private static boolean servingClient = false;

    public enum State {
        ESTABLISHED, SYNRecieve, NONE
    }

    public TCPServerSocketImpl(int port) throws Exception {
        super(port);
        this.PORT = port;
        this.socket = new EnhancedDatagramSocket(this.PORT);
        this.log("TCP: New socket created.");
    }

    public TCPSocket accept() throws Exception {
        byte[] ReceivedData= new byte[NumBytes];
        while(!servingClient){
            DatagramPacket ReceivedPacket = new DatagramPacket(ReceivedData, ReceivedData.length);
            this.socket.receive(ReceivedPacket);
            String PureData = new String(ReceivedPacket.getData());
            InetAddress ClientIP = ReceivedPacket.getAddress();
            int ClientPort = ReceivedPacket.getPort();
            System.out.println("Client port : " + ClientPort);
            System.out.println("TCP Received : " + PureData);
            String[] ProcessedData = PureData.split("\\s+");
            System.out.println(ProcessedData[2]);
            int ReceivedAckNum = Integer.parseInt(ProcessedData[2].trim());
            int ReceivedSequenceNum = Integer.parseInt(ProcessedData[1].trim());

            
            if(this.STATE == State.NONE){
                if(ProcessedData[0].equals("SYN")){ 
                    this.AckNumber = ReceivedSequenceNum + 1;
                    String AckNumber_Str = Integer.toString(this.AckNumber);
                    String SequenceNumber_Str = Integer.toString(this.SequenceNumber);
                    String sendDataStr = "SYN-ACK" + " " + SequenceNumber_Str + " "+ AckNumber_Str;
                    byte[] sendDataBytes = new byte[NumBytes];
                    sendDataBytes = sendDataStr.getBytes();

                    DatagramPacket sendPacket = new DatagramPacket(sendDataBytes, sendDataBytes.length, ClientIP, ClientPort);
                    this.socket.send(sendPacket);
                    this.STATE = State.SYNRecieve;
                }
            }
            else if(this.STATE == State.SYNRecieve){
                if(ProcessedData[0].equals("ACK") && (ReceivedAckNum == (this.SequenceNumber + 1))){
                    this.STATE = State.ESTABLISHED;
                    servingClient = true;
                }
            }
        }
            
        TCPSocketImpl tcpSocket = new TCPSocketImpl("127.0.0.1",1236);
        return tcpSocket;
    }

    public void log(String log_data){
        System.out.println(log_data);
    }

    @Override
    public void close() throws Exception {
        throw new RuntimeException("Not implemented!");
    }
}
