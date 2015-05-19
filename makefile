JC = javac
J = java

default: Client.class Server.class VideoStream.class RTPpacket.class RTCPpacket.class

Client.class: Client.java
	$(JC) $(JFLAGS) Client.java
Server.class: Server.java
	$(JC) $(JFLAGS) Server.java 
VideoStream.class: VideoStream.java
	$(JC) $(JFLAGS) VideoStream.java 
RTPpacket.class: RTPpacket.java
	$(JC) $(JFLAGS) RTPpacket.java 
RTCPpacket.class: RTCPpacket.java
	$(JC) $(JFLAGS) RTCPpacket.java 
clean:
	rm -f *.class
