import java.util.*;
import java.nio.*;

// RR: Receiver Report RTCP Packet

//         0                   1                   2                   3
//         0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// header |V=2|P|    RC   |   PT=RR=201   |             length            |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                     SSRC of packet sender                     |
//        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
// report |                           fraction lost                       |
// block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   1    |              cumulative number of packets lost                |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |           extended highest sequence number received           |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                      interarrival jitter                      |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                         last SR (LSR)                         |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//        |                   delay since last SR (DLSR)                  |
//        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

class RTCPpacket {

    final static int HEADER_SIZE = 8;
    final static int BODY_SIZE = 24;

	public int Version;			// Version number 2
    public int Padding;			// Padding of packet
    public int RC; 				// Reception report count = 1 for one receiver
    public int PayloadType;		// 201 for Receiver Report
    public int length;			// 1 source is always 32 bytes: 8 header, 24 body
    public int Ssrc;			// Ssrc of sender
    public float fractionLost;	// The fraction of RTP data packets from sender lost since the previous RR packet was sent
    public int cumLost;			// The total number of RTP data packets from sender that have been lost since the beginning of reception.
    public int highSeqNb;		// Highest sequence number received
    public int jitter;			// Not used
    public int LSR;				// Not used
    public int DLSR;			// Not used

	public byte[] header;	//Bitstream of header
	public byte[] body;		//Bitstream of the body

    // Constructor from field values
    public RTCPpacket(float fractionLost, int cumLost, int highSeqNb) {
    	Version = 2;
    	Padding = 0;
    	RC = 1;
    	PayloadType = 201;
    	length = 32;
    	//Other fields not used

    	this.fractionLost = fractionLost;
    	this.cumLost = cumLost;
    	this.highSeqNb = highSeqNb;

    	//Construct the bitstreams
    	header = new byte[HEADER_SIZE];
    	body = new byte[BODY_SIZE];

   		header[0] = (byte)(Version << 6 | Padding << 5 | RC);
        header[1] = (byte)(PayloadType & 0xFF);
        header[2] = (byte)(length >> 8);
        header[3] = (byte)(length & 0xFF); 
        header[4] = (byte)(Ssrc >> 24);
        header[5] = (byte)(Ssrc >> 16);
        header[6] = (byte)(Ssrc >> 8);
        header[7] = (byte)(Ssrc & 0xFF);

		ByteBuffer bb = ByteBuffer.wrap(body);
		bb.putFloat(fractionLost);
		bb.putInt(cumLost);
		bb.putInt(highSeqNb);
    }

    // Constructor from bit stream
    public RTCPpacket(byte[] packet, int packet_size) {

    	header = new byte[HEADER_SIZE];
    	body = new byte[BODY_SIZE];

        System.arraycopy(packet, 0, header, 0, HEADER_SIZE);
        System.arraycopy(packet, HEADER_SIZE, body, 0, BODY_SIZE);

    	// Parse header fields
        Version = (header[0] & 0xFF) >> 6;
        PayloadType = header[1] & 0xFF;
        length = (header[3] & 0xFF) + ((header[2] & 0xFF) << 8);
        Ssrc = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);

    	// Parse body fields
    	ByteBuffer bb = ByteBuffer.wrap(body); // big-endian by default
    	fractionLost = bb.getFloat();
    	cumLost = bb.getInt();
    	highSeqNb = bb.getInt();
    }

    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
        //construct the packet = header + body
        System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
        System.arraycopy(body, 0, packet, HEADER_SIZE, BODY_SIZE);

        //return total size of the packet
        return (BODY_SIZE + HEADER_SIZE);
    }

    //--------------------------
    //getlength: return the total length of the RTCP packet
    //--------------------------
    public int getlength() {
        return (BODY_SIZE + HEADER_SIZE);
    }

    public String toString() {
    	return "[RTCP] Version: " + Version + ", Fraction Lost: " + fractionLost 
    		   + ", Cumulative Lost: " + cumLost + ", Highest Seq Num: " + highSeqNb;
    }
}