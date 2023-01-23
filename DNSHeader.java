import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class DNSHeader {

    byte[] idArr;
    short id = 0;   // This identifier is copied to the corresponding reply
                        // and can be used by the requester to match up replies to outstanding queries.
    boolean qr;     // Specifies whether this message is a query (0), or a response (1).
    byte opcode;    // This value is set by the originator of a query and copied into the response.
    boolean aa;     // Authoritative Answer - specifies that the responding name server is an
                        // authority for the domain name in question section.
    boolean tc;     // TrunCation - message was truncated due to length greater than that permitted
    boolean rd;     // Recursion Desired (optional) - this bit may be set in a query and
                        // is copied into the response.
    boolean ra;     // Recursion Available - denotes whether recursive query support is available in the name server
    boolean z;         // Reserved for future use.  Must be zero in all queries and responses.
    boolean ad;        // Authentic Data
    boolean cd;        // Checking Disabled
    byte rcode;     // Response code
    short qdcount;    // the number of entries in the question section
    short ancount;    // the number of resource records in the answer section
    short nscount;    // the number of name server record resources in the authority records section
    short arcount;    // the number of resource records in the additional records section

    public static DNSHeader decodeHeader(InputStream inStream) throws IOException {
        // --read the header from an input stream (we'll use a ByteArrayInputStream but we will only use the
        // basic read methods of input stream to read 1 byte, or to fill in a byte array, so we'll be generic).
        DNSHeader header = new DNSHeader();
        byte[] bytesIn;

        // ID
        header.idArr = inStream.readNBytes(2);
//        System.out.println("ID Array: " + Arrays.toString(header.idArr));

        header.id = (short) (header.idArr[0] << 8);
        header.id = (short) (header.id | (header.idArr[1] & 0xFF));
//        System.out.println("ID: " + String.format("%x",header.id));

        //QR, OPCODE, AA, TC, RD
        bytesIn = inStream.readNBytes(1);
        header.qr = (bytesIn[0] >>> 7) != 0;
        header.opcode = (byte) ((bytesIn[0] & 0x7f) >>> 3);
        header.aa = ((bytesIn[0] & 0x04) >>> 2) != 0;
        header.tc = ((bytesIn[0] & 0x02) >>> 1) != 0;
        header.rd = (bytesIn[0] & 0x01) != 0;

//        System.out.println("QR: " + header.qr);
//        System.out.println("OPCODE: " + header.opcode);
//        System.out.println("AA: " + header.aa);
//        System.out.println("TC: " + header.tc);
//        System.out.println("RD: " + header.rd);

        //RA, Z, AD, CD, RCODE
        bytesIn = inStream.readNBytes(1);
        header.ra = (bytesIn[0] >>> 7) != 0;
        header.z = ((bytesIn[0] & 0x40) >>> 6) != 0;
        header.ad = ((bytesIn[0] & 0x20) >>> 5) != 0;
        header.cd = ((bytesIn[0] & 0x10) >>> 4) != 0;
        header.rcode = (byte) (bytesIn[0] & 0x0F);

//        System.out.println("RA: " + header.ra);
//        System.out.println("Z: " + header.z);
//        System.out.println("AD: " + header.ad);
//        System.out.println("CD: " + header.cd);
//        System.out.println("RCODE: " + header.rcode);

        // QDCOUNT
        bytesIn = inStream.readNBytes(2);
        header.qdcount = (short)(bytesIn[0] << 8);
        header.qdcount = (short)(header.qdcount | bytesIn[1]);
//        System.out.println("QDCOUNT: " + header.qdcount);

        // ANCOUNT
        bytesIn = inStream.readNBytes(2);
        header.ancount = (short)(bytesIn[0] << 8);
        header.ancount = (short)(header.ancount | bytesIn[1]);
//        System.out.println("ANCOUNT: " + header.ancount);

        // NSCOUNT
        bytesIn = inStream.readNBytes(2);
        header.nscount = (short)(bytesIn[0] << 8);
        header.nscount = (short)(header.nscount | bytesIn[1]);
//        System.out.println("NSCOUNT: " + header.nscount);

        // ARCOUNT
        bytesIn = inStream.readNBytes(2);
        header.arcount = (short)(bytesIn[0] << 8);
        header.arcount = (short)(header.arcount | bytesIn[1]);
//        System.out.println("ARCOUNT: " + header.arcount);
//
//        System.out.println("//////////////////////////////////");

//        System.out.println("toString: " + header.toString());

        return header;
    }

    public static DNSHeader buildResponseHeader(DNSMessage request, DNSMessage response){
        // -- This will create the header for the response. It will copy some fields from the request
        DNSHeader responseHeader = request.header;

        responseHeader.qr = true;
        responseHeader.ancount = 1;

        return responseHeader;
    }

    public void writeBytes(OutputStream outStream) throws IOException {
        // --encode the header to bytes to be sent back to the client. The OutputStream interface has methods to
        // write a single byte or an array of bytes.

        outStream.write(idArr);

        byte out = 0;
        if (qr) out = (byte) 0x80;
        if (opcode != 0) out = (byte) (out | (opcode << 3));
        if (aa) out = (byte) (out | 0x4);
        if (tc) out = (byte) (out | 0x2);
        if (rd) out = (byte) (out | 0x1);
        outStream.write(out);

        out = 0;
        if (ra) out = (byte) 0x80;
        if (z) out = (byte) (out | 0x40);
        if (ad) out = (byte) (out | 0x20);
        if (cd) out = (byte) (out | 0x10);
        if (rcode != 0) out = (byte) (out | rcode);
        outStream.write(out);

        if (qdcount != 0) {
            DNSMessage.writeShortToBytes(outStream, qdcount);
        }
        else DNSMessage.writeTwoZeros(outStream);

        if (ancount != 0) {
            DNSMessage.writeShortToBytes(outStream, ancount);
        }
        else DNSMessage.writeTwoZeros(outStream);

        if (nscount != 0) {
            DNSMessage.writeShortToBytes(outStream, nscount);
        }
        else DNSMessage.writeTwoZeros(outStream);

        if (arcount != 0) {
            DNSMessage.writeShortToBytes(outStream, arcount);
        }
        else DNSMessage.writeTwoZeros(outStream);

    }



    @Override
    public String toString() {
        return "DNSHeader{" +
                "idArr=" + Arrays.toString(idArr) +
                ", id=" + String.format("%x", id) +
                ", qr=" + qr +
                ", opcode=" + opcode +
                ", aa=" + aa +
                ", tc=" + tc +
                ", rd=" + rd +
                ", ra=" + ra +
                ", z=" + z +
                ", ad=" + ad +
                ", cd=" + cd +
                ", rcode=" + String.format("%x", rcode) +
                ", qdcount=" + String.format("%x", qdcount) +
                ", ancount=" + String.format("%x", ancount) +
                ", nscount=" + String.format("%x", nscount) +
                ", arcount=" + String.format("%x", arcount) +
                '}';
    }


    // This class should store all the data provided by the 12 byte DNS header.
    // See the spec for all the fields needed.

    // You'll probably need a few getters, but you should NOT provide any setters.
    // Since this is the first thing in a DNS request, you should be able to test
    // that you can read/decode the header before starting on other classes.

}
