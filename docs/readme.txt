README
======

Design/Implementation
---------------------

Both server and client side code have been incorporated with the features 
defined by the project. In addition to having the basic funtionality of the
RTSP and RTP protocols, I have also implemented major componets to support
Session Statistics, RTSP request method DESCRIBE and various performance
optimizations that provides traffic control mechanisms. The following paragraphs
will discuss each of these components implementation in detail.

For providing statistical feedback to the client, I have created a set of labels
in the user GUI to show the statistics on:

- Number of bytes received from client over the entire session
- The fraction of packets that was lost over the entire session
- Video data rate on the current session

To calculate the number of bytes received, an accumulator variable is defined in
the class and gets incremented by the length of every new packet received over
UDP. The fraction of packets lost is based on 2 other parameters: the cumulative
lost packets and the total number of expected packets. The cumulative lost packets
variable is incremented every time the sequence number of a packet is found to be
different from the expected sequence number. The expected packets is based on the
highest number received (in cases where some packets are lost and seqnum is larger
than the counting number). The video data rate is based on the total of number of
bytes received described earlier. This rate is calculated based on the time where
the movie is actually playing and does not count time when the movie is paused.
The client measures the interval between each received packet and sums them up to
find the total time the movie is playing. This is then used for calculating the 
overall data rate statistic.

The DESCRIBE RTSP method is sent directly via the same TCP connection that was
established for RTSP requests. It simply makes the request using the same method
that was used to make other RTSP requests such as SETUP and PLAY. A new response
parsing function is implemented since the response message format for DESCRIBE
is different than the others. The DESCRIBE request is made with the accepted
file type to be "appliation/sdp", so we are expecting an SDP formatted string in
the body of the response message. On the server side, methods have been added
to handle an incoming DESCRIBE request. The response will be created in SDP format
following the SDP specifications. The following information is provided back to
the requester:

- RTSP port of the server
- Video encoding of the video transmitted over RTP
- RTSP ID of the established session

In the client GUI a "Session" button have been created so the user can test the
DESCRIBE method functionality. You should see printout to stdout of the response
coming back from the server when the Session button is clicked. Note that in 
order to use Session method the SETUP request must have been called already so
the server is in INIT state.

Server and client both contain optimizations and traffic control mechanisms. The
framework providing communication between client and server on QoS data is the
RTCP protocol described under RFC 1889. For this I created a new class called 
RTCPpacket that represents a RTCP packet. Specifics can be found on the RFC. The
RTCP protocol is built on top of the UDP transport protocol to have minimal overhead.
The amount of RTCP packets send via UDP is much less than RTP to minimize traffic. On 
the server side, the class responsible for congestion control is CongestionController.
It periodically checks the statistics feedback from client (via RTCP packets) and
adjusts the rate to send RTP packets to the client. Congestion level is divided
into 4 levels from 1 to 4 represented by the congestionLevel variable, with 0 
being not congested. Calculations on the congestion level are determined primarily
by the fractionLost field in the RTCP packet sent by the client.

In addtion to Congestion Control, the server side also provides adaptive video
compression/encoding to optimize the size of video frames to send to the client
under heavy traffic conditions. The ImageTranslator class does the job of compressing
a video frame bitstream into a lower quality. By sacrificing a lower quality video,
smaller packets are being sent over the wire and thereby reduces the congestion
in the network.

On the client side, the class FrameSynchronizer is created to solve the problem
of organizing frames that was sent over out of order sequence numbers. It achieves
this by using a buffer and keeps track of the expected sequence number versus
the actual sequence number received. If there is a discrepancy, it buffers the
frame and fills the gap with copies of the old frame that was last shown. The
algorithm in this Synchronizer solves the problem of the FunkyServer class. See 
below Extra section for explaination.


FunkyServer (Extra)
-------------------
The problem with FunkyServer is that it sends old frames intermittently. As an
example, the sequence number it sends to the client would be:

348, 349, 350, 351, 352, 308, 308, 308, 308, 308, 308, 359

See that it replaced frames between frames 352 and 359 with 308. My client program
has created a class FrameSynchronizer that will fix this problem. Because a video
should not "go back in time", i.e go from 352 to 308, the appropriate frame numbers 
to show the audience in the above example should be:

348, 349, 350, 351, 352, 352, 352, 352, 352, 352, 352, 359

FrameSynchronizer will buffer frames and check for incoming sequence number. If
they are out of sync, it copies the latest frame and fills in the gap in the missed
frames. That way the video would just "freeze" and not show the "go back in time"
effect shown previously.


Main Method
-----------
Both server and client each contain a main method and should be executed separately
on a server machine and client machine. Or they could both be executed on the same 
machine but running in separate terminal processes.


HOWTO Run Client/Server Programs
-------------------------------------------
First, compile all *.java files, please use the makefile provided. Run the following
commands in bash to compile the java source code (tested with java version 1.7):

$ make

I've already created the following scripts to run each of the components in this
project:

run_server - runs the server
run_client - runs the client
run_funky - runs the FunkyServer

After compiling the files, simply type the following commands to run each program:

$ ./run_server
$ ./run_client
$ ./run_funky   <-- Copy FunkyServer class files from extra/ before running this

Note that the client and server are assumed to be running on the same computer 
i.e. localhost. By default, server ip address is localhost and port number is 1051. 
If for some reason any of these parameters need to be changed, simply open 
the run_* files and modify the address and port variables.




















