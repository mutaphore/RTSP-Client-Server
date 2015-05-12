import java.util.*;

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

	public int Version;
    public int Padding;
    public int RC; 		// Reception report count, 1 for now
    public int PayloadType;
    public int length;	// 1 source is always 32bytes: 8 header, 24 body
    public int Ssrc;
    public int fractionLost;
    public int cumLost;
    public int highSeqNb;	// Highest sequence number received
    public int jitter;
    public int LSR;
    public int DLSR;

    public byte[] header;	//Bitstream of header
    public byte[] body;		//Bitstream of the body

    // Constructor from field values
    public RTCPpacket(int fractionLost, int cumLost, int highSeqNb) {
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
        header[1] = (byte)(PayloadType & 0x000000FF);
        header[2] = (byte)(length >> 8);
        header[3] = (byte)(length & 0xFF); 
        header[4] = (byte)(Ssrc >> 24);
        header[5] = (byte)(Ssrc >> 16);
        header[6] = (byte)(Ssrc >> 8);
        header[7] = (byte)(Ssrc & 0xFF);

		ByteBuffer bb = ByteBuffer.wrap(body);
		bb.putInt(fractionLost);
		bb.putInt(cumLost);
		bb.putInt(highSeqNb);
		bb.putInt(jitter);
		bb.putInt(LSR);
		bb.putInt(DLSR);
    }

    // Constructor from bit stream
    public RTCPpacket(byte[] packet, int packet_size) {
    	ByteBuffer wrapped = ByteBuffer.wrap(packet); // big-endian by default
		Version = wrapped.getInt();
    }

    //--------------------------
    //getpacket: returns the packet bitstream and its length
    //--------------------------
    public int getpacket(byte[] packet)
    {
        //construct the packet = header + body
        for (int i=0; i < HEADER_SIZE; i++)
            packet[i] = header[i];
        for (int i=0; i < BODY_SIZE; i++)
            packet[i+HEADER_SIZE] = body[i];

        //return total size of the packet
        return(BODY_SIZE + HEADER_SIZE);
    }

    //--------------------------
    //getlength: return the total length of the RTCP packet
    //--------------------------
    public int getlength() {
        return(BODY_SIZE + HEADER_SIZE);
    }

}