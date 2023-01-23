import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DNSMessage {

    ByteArrayInputStream byteInStream;
    DNSHeader header;
    ArrayList<DNSQuestion> questions = new ArrayList<>();
    ArrayList<DNSRecord> answers = new ArrayList<>();
    ArrayList<DNSRecord> authorityRecords = new ArrayList<>();
    ArrayList<DNSRecord> additionalRecords = new ArrayList<>();
    byte[] bytes;

    /**
     * Decodes incoming byte array into DNSMessage
     * @param bytes - incoming byte array from DatagramPacket
     * @return the decoded byte array as a DNSMessage
     * @throws IOException (reading from ByteArrayInputStream)
     */
    static DNSMessage decodeMessage(byte[] bytes) throws IOException {
        DNSMessage msg = new DNSMessage();
        msg.bytes = bytes;
        msg.byteInStream = new ByteArrayInputStream(bytes);

        // Header
        msg.header = DNSHeader.decodeHeader(msg.byteInStream);
//        System.out.println("Msg Header: " + msg.header);


        // Questions
        for (int i=0; i<msg.header.qdcount; i++) {
            msg.questions.add(DNSQuestion.decodeQuestion(msg.byteInStream, msg));
        }
//        System.out.println("Msg Questions: " + msg.questions);


        // Answers
        for (int i=0; i<msg.header.ancount; i++) {
            msg.answers.add(DNSRecord.decodeRecord(msg.byteInStream, msg));
        }
//        System.out.println("Msg Answers: " + msg.answers);


        // Authority Records
        for (int i=0; i<msg.header.nscount; i++) {
            msg.authorityRecords.add(DNSRecord.decodeRecord(msg.byteInStream, msg));
        }
//        System.out.println("Msg Auth Recs: " + msg.authorityRecords);


        // Additional Records
        for (int i=0; i<msg.header.arcount; i++) {
            msg.additionalRecords.add(DNSRecord.decodeRecord(msg.byteInStream, msg));
        }
//        System.out.println("Msg Add Recs: " + msg.additionalRecords);
//        System.out.println("");

        //Return the decoded DNSMessage
        return msg;
    }

    /**
     * Reads domain name from input stream and returns an ArrayList of the domain
     * name substrings. The first byte read is the length of the first substring,
     * followed by the ascii char values for the substring. The domain name ends
     * with a 0-byte.
     * @param inStream - read in the domain name from the input stream
     * @return an Arraylist<String> containing the domain name substrings
     * @throws IOException (reading from ByteArrayInputStream)
     */
    ArrayList<String> readDomainName(InputStream inStream) throws IOException {
        // --read the pieces of a domain name starting from the current position of the input stream
        ArrayList<String> domainArr = new ArrayList<>();
        byte[] bytesIn;
        byte[] length = inStream.readNBytes(1);

        // stop looping when a 0-byte is found
        while (length[0] != 0){
            bytesIn = inStream.readNBytes(length[0]);   // read in # of bytes equal to substring length
            String str = new String(bytesIn);           // convert byte array to string
            domainArr.add(str);

            length = inStream.readNBytes(1);        // get next substring length
        }

        return domainArr;
    }

    /**
     * In the case of compression, passes in the domain name offset. The first two
     * bits are set, indicating compression. Removes the first two bits and skips
     * the remaining offset value into a new instantiation of the ByteArrayInputStream
     * and calls the readDomainName method above.
     * @param first2Bytes - the offset value (a short with the first 2 bits set)
     * @return an Arraylist<String> containing the domain name substrings
     * @throws IOException (reading from ByteArrayInputStream)
     */
    ArrayList<String> readDomainName(int first2Bytes) throws IOException {
        // new instantiation of the ByteArrayInputStream to find where the domain
        // name was written earlier
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);

        int offset = (first2Bytes ^ 0xC000);            // xor to unset first 2 bits

        int offsetCheck = (int) byteStream.skip(offset);
        if (offset != offsetCheck){                     // skip returns actual number of bytes skipped
            throw new StreamCorruptedException();
        }

        return readDomainName(byteStream);              // read full domain name from input stream
    }

    /**
     * Build a DNSMessage to send back to the client, using an answer from the cache
     * @param request - the client's DNSMessage
     * @param answer - the answer to the client's query from the cache
     * @return  a DNSMessage with the answer to the client's question
     */
    static DNSMessage buildResponse(DNSMessage request, DNSRecord answer){
        DNSMessage response = new DNSMessage();

        response.header = DNSHeader.buildResponseHeader(request, response);
//        System.out.println("Response Header: " + response.header);

        // copy the questions from the Request
        response.questions = request.questions;
//        System.out.println("Response Questions: " + response.questions);

        // add the answer from the cache to the arraylist of answers
        response.answers.add(answer);
//        System.out.println("Response Answers: " + response.answers);

        // copy the authority records from the Request
        response.authorityRecords = request.authorityRecords;
//        System.out.println("Response Auth Recs: " + response.authorityRecords);

        // copy the additional records from the Request
        response.additionalRecords = request.additionalRecords;
//        System.out.println("Response Add Recs: " + response.additionalRecords);

        return response;
    }

    /**
     *
     * @param response
     * @return
     * @throws IOException
     */
    byte[] toBytes(DNSMessage response) throws IOException {
        // -- get the bytes to put in a packet and send back
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        HashMap<String, Integer> domainNameLocations = new HashMap<>();

        response.header.writeBytes(bytesOut);

        for (DNSQuestion q : response.questions){
            q.writeBytes(bytesOut, domainNameLocations);
        }

        for (DNSRecord r : response.answers){
            r.writeBytes(bytesOut, domainNameLocations);
        }

        for (DNSRecord r : response.authorityRecords){
            r.writeBytes(bytesOut, domainNameLocations);
        }

        for (DNSRecord r : response.additionalRecords){
            r.writeBytes(bytesOut, domainNameLocations);
        }

        return bytesOut.toByteArray();
    }

    static void writeDomainName(ByteArrayOutputStream byteOutStream,
                                HashMap<String,Integer> domainLocations, ArrayList<String> domainPieces){
        //  -- If this is the first time we've seen this domain name in the packet, write it using the DNS encoding
        //  (each segment of the domain prefixed with its length, 0 at the end), and add it to the hash map.
        //  Otherwise, write a back pointer to where the domain has been seen previously.

        String domainStr = String.join(".", domainPieces);

        if (!domainLocations.containsKey(domainStr)){
            domainLocations.put(domainStr, byteOutStream.size());

            for (String s : domainPieces){
                byteOutStream.write(s.length());
                for (int i=0; i<s.length(); i++){
                    char c = s.charAt(i);
                    byteOutStream.write((int) c);
                }
            }
            byteOutStream.write(0);
        }
        else {
            int offset = domainLocations.get(domainStr);
            byteOutStream.write((offset >>> 8) | 0xC0);
            byteOutStream.write(offset);
        }
    }

    static void writeShortToBytes(OutputStream out, short sh) throws IOException {
        out.write(sh >>> 8);
        out.write(sh);
    }

    static void writeTwoZeros(OutputStream out) throws IOException {
        out.write(0);
        out.write(0);
    }

    String octetsToString(String[] octets){
        // -- join the pieces of a domain name with dots ([ "utah", "edu"] -> "utah.edu" )

        return null;
    }

    @Override
    public String toString() {
        return "DNSMessage{" +
                "byteInStream=" + byteInStream +
                ", header=" + header +
                ", questions=" + questions +
                ", records=" + answers +
                ", bytes=" + Arrays.toString(bytes) +
                '}';
    }
}
