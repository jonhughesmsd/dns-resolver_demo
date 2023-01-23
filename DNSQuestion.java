import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public class DNSQuestion {

    ArrayList<String> qName = new ArrayList<>();
    short qType;
    short qClass;

    public static DNSQuestion decodeQuestion(InputStream inStream, DNSMessage msg) throws IOException {
        // -- read a question from the input stream. Due to compression, you may have to ask the
        // DNSMessage containing this question to read some of the fields.
        DNSQuestion question = new DNSQuestion();
        byte[] bytesIn;

        //Read in URL
        question.qName = msg.readDomainName(inStream);

        bytesIn = inStream.readNBytes(2);
        question.qType = (short) (bytesIn[0] << 8);
        question.qType = (short) (question.qType | (bytesIn[1] & 0xFF));

        bytesIn = inStream.readNBytes(2);
        question.qClass = (short) (bytesIn[0] << 8);
        question.qClass = (short) (question.qClass | (bytesIn[1] & 0xFF));

//        System.out.println("toString: " + question.toString());

        return question;
    }
    public void writeBytes(ByteArrayOutputStream byteOutStream, HashMap<String,Integer> domainNameLocations) throws IOException {
        // Write the question bytes which will be sent to the client. The hash map is used for us to
        // compress the message, see the DNSMessage class below.

        DNSMessage.writeDomainName(byteOutStream, domainNameLocations, qName);

        DNSMessage.writeShortToBytes(byteOutStream, qType);

        DNSMessage.writeShortToBytes(byteOutStream, qClass);

    }

    // They're needed to use a question as a HashMap key, and to get a human readable string.


    @Override
    public String toString() {
        return "DNSQuestion{" +
                "qName=" + Arrays.toString(qName.toArray()) +
                ", qType=" + String.format("%x", qType) +
                ", qClass=" + String.format("%x", qClass) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSQuestion that = (DNSQuestion) o;
        return qType == that.qType && qClass == that.qClass && qName.equals(that.qName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qName, qType, qClass);
    }
}
