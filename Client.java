/* ------------------
   Client
   usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
   ---------------------- */

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Client{

    //GUI
    //----
    JFrame f = new JFrame("Client");
    JButton setupButton = new JButton("Setup");
    JButton playButton = new JButton("Play");
    JButton pauseButton = new JButton("Pause");
    JButton tearButton = new JButton("Close");
    JButton describeButton = new JButton("Session");
    JPanel mainPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JLabel statLabel1 = new JLabel();
    JLabel statLabel2 = new JLabel();
    JLabel statLabel3 = new JLabel();
    JLabel iconLabel = new JLabel();
    ImageIcon icon;

    //RTP variables:
    //----------------
    DatagramPacket rcvdp; //UDP packet received from the server
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets
    
    Timer timer; //timer used to receive data from the UDP socket
    byte[] buf; //buffer used to store data received from the server 
   
    //RTSP variables
    //----------------
    //rtsp states 
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state; //RTSP state == INIT or READY or PLAYING
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file to request to the server
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

    final static String CRLF = "\r\n";
    final static String DES_FNAME = "session_info.txt";

    //RTCP variables
    //----------------
    DatagramSocket RTCPsocket;
    static int RTCP_RCV_PORT = 25001; //port where the client will receive the RTP packets
    Timer controlTimer;

    //Video constants:
    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video

    //Statistics variables:
    //------------------
    int statTotalBytes;         //Total number of bytes received in a session
    double statStartTime;       //Time in milliseconds when start is pressed
    double statTotalPlayTime;   //Time in milliseconds of video playing since beginning
    int statPktLost;            //Number of packets lost
    int statRTPNb;              //Sequence number of expected RTP messages within the session
   
    //--------------------------
    //Constructor
    //--------------------------
    public Client() {

        //build GUI
        //--------------------------
     
        //Frame
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        //Buttons
        buttonPanel.setLayout(new GridLayout(1,0));
        buttonPanel.add(setupButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(tearButton);
        buttonPanel.add(describeButton);
        setupButton.addActionListener(new setupButtonListener());
        playButton.addActionListener(new playButtonListener());
        pauseButton.addActionListener(new pauseButtonListener());
        tearButton.addActionListener(new tearButtonListener());
        describeButton.addActionListener(new describeListener());

        //Statistics
        statLabel1.setText("Total Bytes Received: 0");
        statLabel2.setText("Packets Lost: 0");
        statLabel3.setText("Data Rate (bytes/sec): 0");

        //Image display label
        iconLabel.setIcon(null);
        
        //frame layout
        mainPanel.setLayout(null);
        mainPanel.add(iconLabel);
        mainPanel.add(buttonPanel);
        mainPanel.add(statLabel1);
        mainPanel.add(statLabel2);
        mainPanel.add(statLabel3);
        iconLabel.setBounds(0,0,380,280);
        buttonPanel.setBounds(0,280,380,50);
        statLabel1.setBounds(0,330,380,20);
        statLabel2.setBounds(0,350,380,20);
        statLabel3.setBounds(0,370,380,20);

        f.getContentPane().add(mainPanel, BorderLayout.CENTER);
        f.setSize(new Dimension(380,420));
        f.setVisible(true);

        //init timer
        //--------------------------
        timer = new Timer(20, new timerListener());
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        // Fires 5% of the time
        controlTimer = new Timer(400, new controlTimerListener());
        controlTimer.setInitialDelay(0);
        controlTimer.setCoalesce(true);

        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[15000];    
    }

    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception
    {
        //Create a Client object
        Client theClient = new Client();
        
        //get server RTSP port and IP address from the command line
        //------------------
        int RTSP_server_port = Integer.parseInt(argv[1]);
        String ServerHost = argv[0];
        InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

        //get video filename to request:
        VideoFileName = argv[2];

        //Establish a TCP connection with the server to exchange RTSP messages
        //------------------
        theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

        //Establish a UDP connection with the server to exchange RTCP control packets
        //------------------

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()) );

        //init RTSP state:
        state = INIT;
    }


    //------------------------------------
    //Handler for buttons
    //------------------------------------

    //.............
    //TO COMPLETE
    //.............

    //Handler for Setup button
    //-----------------------
    class setupButtonListener implements ActionListener{

        public void actionPerformed(ActionEvent e){

            System.out.println("Setup Button pressed !");      
            if (state == INIT) {
                //Init non-blocking RTPsocket that will be used to receive data
                try {
                    //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
                    RTPsocket = new DatagramSocket(RTP_RCV_PORT);
                    //UDP socket for sending QoS RTCP packets
                    RTCPsocket = new DatagramSocket();
                    //set TimeOut value of the socket to 5msec.
                    RTPsocket.setSoTimeout(5);
                }
                catch (SocketException se)
                {
                    System.out.println("Socket exception: "+se);
                    System.exit(0);
                }

                //init RTSP sequence number
                RTSPSeqNb = 1;

                //Send SETUP message to the server
                send_RTSP_request("SETUP");

                //Wait for the response 
                if (parse_server_response() != 200)
                    System.out.println("Invalid Server Response");
                else 
                {
                    //change RTSP state and print new state 
                    state = READY;
                    System.out.println("New RTSP state: READY");
                }
            }
            //else if state != INIT then do nothing
        }
    }
    
    //Handler for Play button
    //-----------------------
    class playButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            System.out.println("Play Button pressed!"); 

            //Start to save the time in stats
            statStartTime = System.currentTimeMillis();

            if (state == READY) {
                //increase RTSP sequence number
                RTSPSeqNb++;

                //Send PLAY message to the server
                send_RTSP_request("PLAY");

                //Wait for the response 
                if (parse_server_response() != 200) {
                    System.out.println("Invalid Server Response");
                }
                else {
                    //change RTSP state and print out new state
                    state = PLAYING;
                    System.out.println("New RTSP state: PLAYING");

                    //start the timer
                    timer.start();
                }
            }
            //else if state != READY then do nothing
        }
    }


    //Handler for Pause button
    //-----------------------
    class pauseButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e){

            System.out.println("Pause Button pressed!");   

            if (state == PLAYING) 
            {
                //increase RTSP sequence number
                RTSPSeqNb++;

                //Send PAUSE message to the server
                send_RTSP_request("PAUSE");

                //Wait for the response 
                if (parse_server_response() != 200)
                    System.out.println("Invalid Server Response");
                else 
                {
                    //change RTSP state and print out new state
                    state = READY;
                    System.out.println("New RTSP state: READY");
                      
                    //stop the timer
                    timer.stop();
                }
            }
            //else if state != PLAYING then do nothing
        }
    }

    //Handler for Teardown button
    //-----------------------
    class tearButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e){

            System.out.println("Teardown Button pressed !");  

            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send TEARDOWN message to the server
            send_RTSP_request("TEARDOWN");

            //Wait for the response 
            if (parse_server_response() != 200)
                System.out.println("Invalid Server Response");
            else {     
                //change RTSP state and print out new state
                state = INIT;
                System.out.println("New RTSP state: INIT");

                //stop the timer
                timer.stop();

                //exit
                System.exit(0);
            }
        }
    }

    // Get information about the data stream
    class describeListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            System.out.println("Sending DESCRIBE request");  

            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send DESCRIBE message to the server
            send_RTSP_request("DESCRIBE");

            //Wait for the response 
            if (parse_server_response() != 200) {
                System.out.println("Invalid Server Response");
            }
            else {     
                System.out.println("Received response for DESCRIBE");
            }
        }
    }

    //------------------------------------
    //Handler for timer
    //------------------------------------
    
    class timerListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
          
            //Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(buf, buf.length);

            try {
                //receive the DP from the socket, save time for stats
                RTPsocket.receive(rcvdp);
                statTotalPlayTime += System.currentTimeMillis() - statStartTime; 
                //keep track of sequence number for dropped packets stats
                statRTPNb++;

                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

                //print important header fields of the RTP packet received: 
                System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber()
                                   + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type "
                                   + rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the RTPpacket object
                int payload_length = rtp_packet.getpayload_length();
                byte [] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);

                //compute stats
                if (statRTPNb != rtp_packet.getsequencenumber())
                    statPktLost++;
                statTotalBytes += payload_length;
                setStats();

                //get an Image object from the payload bitstream
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Image image = toolkit.createImage(payload, 0, payload_length);

                //display the image as an ImageIcon object
                icon = new ImageIcon(image);
                iconLabel.setIcon(icon);
            }
            catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }

            // Start capturing the time for the next cycle
            statStartTime = System.currentTimeMillis();
        }
    }

    // Sends RTCP control packets at designated intervals
    class controlTimerListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            DatagramPacket ctrldp;
            byte[] 
            int packet_length;

            try {

                ctrldp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
                RTCPsocket.send(ctrldp);

            } catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }
    }

    //------------------------------------
    //Parse Server Response
    //------------------------------------
    private int parse_server_response() 
    {
        int reply_code = 0;

        try {
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Client - Received from Server:");
            System.out.println(StatusLine);
          
            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); //skip over the RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());
            
            //if reply code is OK get and print the 2 other lines
            if (reply_code == 200) {
                String SeqNumLine = RTSPBufferedReader.readLine();
                System.out.println(SeqNumLine);
                
                String SessionLine = RTSPBufferedReader.readLine();
                System.out.println(SessionLine);

                tokens = new StringTokenizer(SessionLine);
                String temp = tokens.nextToken();
                //if state == INIT gets the Session Id from the SessionLine
                if (temp.compareTo("Session:") == 0 && state == INIT) {
                    RTSPid = Integer.parseInt(tokens.nextToken());
                }
                else if (temp.compareTo("Content-Base:") == 0) {
                    // Get the DESCRIBE lines
                    String newLine;
                    for (int i = 0; i < 6; i++) {
                        newLine = RTSPBufferedReader.readLine();
                        System.out.println(newLine);
                    }
                }
            }
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
      
        return(reply_code);
    }

    private void setStats() {
        double dataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
        DecimalFormat formatter = new DecimalFormat("###,###.#");
        statLabel1.setText("Total Bytes Received: " + statTotalBytes);
        statLabel2.setText("Packets Lost: " + statPktLost);
        statLabel3.setText("Data Rate: " + formatter.format(dataRate) + " bytes/s");
    }

    //------------------------------------
    //Send RTSP Request
    //------------------------------------

    //.............
    //TO COMPLETE
    //.............
    
    private void send_RTSP_request(String request_type)
    {
        try {
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);

            //write the CSeq line: 
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            //check if request_type is equal to "SETUP" and in this case write the 
            //Transport: line advertising to the server the port used to receive 
            //the RTP packets RTP_RCV_PORT
            if (request_type == "SETUP") {
                RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
            }
            else if (request_type == "DESCRIBE") {
                RTSPBufferedWriter.write("Accept: application/sdp, application/rtsl, application/mheg" + CRLF);
            }
            else {
                //otherwise, write the Session line from the RTSPid field
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }

            RTSPBufferedWriter.flush();
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }    
}

//end of Class Client
