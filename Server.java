/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */


import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Server extends JFrame implements ActionListener 
{

    //RTP variables:
    //----------------
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    DatagramPacket senddp; //UDP packet containing the video frames

    InetAddress ClientIPAddr; //Client IP address
    int RTP_dest_port = 0; // destination port for RTP packets  (given by the RTSP Client)
    int RTSP_dest_port = 0;

    //GUI:
    //----------------
    JLabel label;

    //Video variables:
    //----------------
    int imagenb = 0; //image nb of the image currently transmitted
    VideoStream video; //VideoStream object used to access video frames
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
    static int VIDEO_LENGTH = 500; //length of the video in frames

    Timer timer; //timer used to send the images at the video frame rate
    byte[] buf; //buffer used to store the images to send to the client 

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;
    final static int DESCRIBE = 7;

    static int state; //RTSP Server state == INIT or READY or PLAY
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file requested from the client
    static int RTSP_ID = 123456; //ID of the RTSP session
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session


    //RTCP variables
    //----------------
    DatagramSocket RTCPsocket;
    Timer rtcpTimer;
    byte[] rtcpBuf;
    static int RTCP_RCV_PORT = 25001; //port where the client will receive the RTP packets
    static int CTRL_PERIOD = 100;     //How often to check for control events
    
    final static String CRLF = "\r\n";

    //--------------------------------
    //Constructor
    //--------------------------------
    public Server(){

        //init Frame
        super("Server");

        //init Timer
        timer = new Timer(FRAME_PERIOD, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        rtcpTimer = new Timer(CTRL_PERIOD, new RTCPListener());
        rtcpTimer.setInitialDelay(0);
        rtcpTimer.setCoalesce(true);

        //allocate memory for the sending buffer
        buf = new byte[15000]; 

        //allocate buffer for receiving RTCP packets
        rtcpBuf = new byte[100];

        //Handler to close the main window
        addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          //stop the timer and exit
            timer.stop();
            System.exit(0);
        }});

        //GUI:
        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);
    }
          
    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception
    {
        //create a Server object
        Server theServer = new Server();

        //show GUI:
        theServer.pack();
        theServer.setVisible(true);

        //get RTSP socket port from the command line
        int RTSPport = Integer.parseInt(argv[0]);
        theServer.RTSP_dest_port = RTSPport;
       
        //Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(RTSPport);
        theServer.RTSPsocket = listenSocket.accept();
        listenSocket.close();

        //Get Client IP address
        theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

        //Initiate RTSPstate
        state = INIT;

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()) );
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()) );

        //Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        while(!done) {
            request_type = theServer.parse_RTSP_request(); //blocking
    
            if (request_type == SETUP) {
                done = true;

                //update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");
             
                //Send response
                theServer.send_RTSP_response();
             
                //init the VideoStream object:
                theServer.video = new VideoStream(VideoFileName);

                //init RTP and RTCP sockets
                theServer.RTPsocket = new DatagramSocket();
                theServer.RTCPsocket = new DatagramSocket(RTCP_RCV_PORT);
            }
        }

        //loop to handle RTSP requests
        while(true) {
            //parse the request
            request_type = theServer.parse_RTSP_request(); //blocking
                
            if ((request_type == PLAY) && (state == READY)) {
                //send back response
                theServer.send_RTSP_response();
                //start timer
                theServer.timer.start();
                //update state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");
            }
            else if ((request_type == PAUSE) && (state == PLAYING)) {
                //send back response
                theServer.send_RTSP_response();
                //stop timer
                theServer.timer.stop();
                //update state
                state = READY;
                System.out.println("New RTSP state: READY");
            }
            else if (request_type == TEARDOWN) {
                //send back response
                theServer.send_RTSP_response();
                //stop timer
                theServer.timer.stop();
                //close sockets
                theServer.RTSPsocket.close();
                theServer.RTPsocket.close();

                System.exit(0);
            }
            else if (request_type == DESCRIBE) {
                System.out.println("Received DESCRIBE request");
                theServer.send_RTSP_describe();
            }
        }
    }

    //------------------------
    //Handler for timer
    //------------------------
    public void actionPerformed(ActionEvent e) {

        //if the current image nb is less than the length of the video
        if (imagenb < VIDEO_LENGTH) {
            //update current imagenb
            imagenb++;

            try {
                //get next frame to send from the video, as well as its size
                int image_length = video.getnextframe(buf);

                //Builds an RTPpacket object containing the frame
                RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, buf, image_length);
                
                //get to total length of the full rtp packet to send
                int packet_length = rtp_packet.getlength();

                //retrieve the packet bitstream and store it in an array of bytes
                byte[] packet_bits = new byte[packet_length];
                rtp_packet.getpacket(packet_bits);

                //send the packet as a DatagramPacket over the UDP socket 
                senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
                RTPsocket.send(senddp);

                System.out.println("Send frame #"+imagenb);
                //print the header bitstream
                rtp_packet.printheader();

                //update GUI
                label.setText("Send frame #" + imagenb);
            }
            catch(Exception ex) {
                System.out.println("Exception caught: "+ex);
                System.exit(0);
            }
        }
        else {
            //if we have reached the end of the video file, stop the timer
            timer.stop();
        }
    }

    // A listener for RTCP packets sent from client
    public class RTCPListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            //Construct a DatagramPacket to receive data from the UDP socket
            DatagramPacket dp = new DatagramPacket(rtcpBuf, rtcpBuf.length);

            try {
                RTCPsocket.receive(dp);   // Blocking
                RTCPpacket packet = new RTCPpacket(dp.getData(), dp.getLength());
                System.out.println("Received RTCP packet");
                System.out.println(packet);
            }
            catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }
    }

    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parse_RTSP_request()
    {
        int request_type = -1;
        try { 
            //parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;
            else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
                request_type = DESCRIBE;

            if (request_type == SETUP) {
                //extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }

            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());
        
            //get LastLine
            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);

            tokens = new StringTokenizer(LastLine);
            if (request_type == SETUP) {
                //extract RTP_dest_port from LastLine
                for (int i=0; i<3; i++)
                    tokens.nextToken(); //skip unused stuff
                RTP_dest_port = Integer.parseInt(tokens.nextToken());
            }
            else if (request_type == DESCRIBE) {
                // tokens.nextToken();
                // String describeDataType = tokens.nextToken();
            }
            else {
                //otherwise LastLine will be the SessionId line
                tokens.nextToken(); //skip Session:
                RTSP_ID = Integer.parseInt(tokens.nextToken());
            }
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
      
        return(request_type);
    }

    // Creates a DESCRIBE response string in SDP format for current media
    private String createDescribeSDP() {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        
        // Write the body first so we can get the size later
        writer2.write("v=0" + CRLF);
        writer2.write("m=video " + RTSP_dest_port + " RTP/AVP " + MJPEG_TYPE + CRLF);
        writer2.write("a=control:streamid=" + RTSP_ID + CRLF);
        writer2.write("a=mimetype:string;\"video/MJPEG\"" + CRLF);
        String body = writer2.toString();

        writer1.write("Content-Base: " + VideoFileName + CRLF);
        writer1.write("Content-Type: " + "application/sdp" + CRLF);
        writer1.write("Content-Length: " + body.length() + CRLF);
        writer1.write(body);
        
        return writer1.toString();
    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    private void send_RTSP_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }

    private void send_RTSP_describe() {
        String des = createDescribeSDP();
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write(des);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }
}