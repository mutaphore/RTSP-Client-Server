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

Server and client both contain optimizations and traffic control mechanisms. The
framework providing communication between client and server on QoS data is the
RTCP protocol described under RFC 1889. For this I created a new class called 
RTCPpacket that represents a RTCP packet. Specifics can be found on the RFC. On the
server side, the class responsible for congestion control is CongestionController.
It periodically checks the statistics feedback from client (via RTCP packets) and
adjusts the rate to send RTP packets to the client. Congestion level is divided
into 4 levels from 1 to 4 represented by the congestionLevel variable, with 0 
being not congested. Calculations on the congestion level are determined primarily
by the fractionLost field in the RTCP packet sent by the client

Main Method
-----------
Both server and client each contain a main method and should be executed separately
on a server machine and client machine. They could also be executed on the same 
machine but each running in a different terminal process.