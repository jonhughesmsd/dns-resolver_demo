import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DNSRecord {

    ArrayList<String> name;
    short type;
    short rClass;
    int ttl;
    long expiry;
    long timestamp;
    short rdlength;
    byte[] rdata;

    static DNSRecord decodeRecord(InputStream inStream, DNSMessage msg) throws IOException {
        DNSRecord record = new DNSRecord();
        byte[] bytesIn;

        inStream.mark(2);
        bytesIn = inStream.readNBytes(1);
        boolean compressed = (bytesIn[0] & 0xC0) == 0xC0;

        if (compressed) {
            int first2Bytes = bytesIn[0] << 8;
            bytesIn = inStream.readNBytes(1);
            first2Bytes = (first2Bytes | (bytesIn[0] & 0xFF)) & 0xFFFF;
            record.name = msg.readDomainName(first2Bytes);
        }
        else {
            inStream.reset();
            record.name = msg.readDomainName(inStream);
        }

        bytesIn = inStream.readNBytes(2);
        record.type = (short) (bytesIn[0] << 8);
        record.type = (short) (record.type | (bytesIn[1] & 0xFF));

        bytesIn = inStream.readNBytes(2);
        record.rClass = (short) (bytesIn[0] << 8);
        record.rClass = (short) (record.rClass | (bytesIn[1] & 0xFF));

        bytesIn = inStream.readNBytes(4);
        record.ttl = bytesIn[0] << 24;
        record.ttl = record.ttl | ((bytesIn[1] & 0xFF) << 16);
        record.ttl = record.ttl | ((bytesIn[2] & 0xFF) << 8);
        record.ttl = record.ttl | (bytesIn[3] & 0xFF);

        record.timestamp = Instant.now().getEpochSecond();
        record.expiry = record.timestamp + record.ttl;
//        record.expiry = Instant.now().getEpochSecond() + 15;

//        System.out.println("Epoch time: " + String.format("%x", Instant.now().getEpochSecond()));
//        System.out.println("TTL: " + String.format("%x", record.ttl));
//        System.out.println("Expiry: " + String.format("%x", record.expiry));

        bytesIn = inStream.readNBytes(2);
        record.rdlength = (short) (bytesIn[0] << 8);
        record.rdlength = (short) (record.rdlength | (bytesIn[1] & 0xFF));

        if (record.rdlength > 0) {
            record.rdata = inStream.readNBytes(record.rdlength);
        }

//        System.out.println("toString: " + record);

        return record;
    }

    public void writeBytes(ByteArrayOutputStream byteOutStream, HashMap<String, Integer> domainNameLocations) throws IOException {

        DNSMessage.writeDomainName(byteOutStream, domainNameLocations, name);

        DNSMessage.writeShortToBytes(byteOutStream, type);

        DNSMessage.writeShortToBytes(byteOutStream, rClass);

        byteOutStream.write(ttl >>> 24);
        byteOutStream.write(ttl >>> 16);
        byteOutStream.write(ttl >>> 8);
        byteOutStream.write(ttl);

        DNSMessage.writeShortToBytes(byteOutStream, rdlength);

        if (rdlength > 0) {
            byteOutStream.write(rdata);
        }

    }

    @Override
    public String toString() {
        return "DNSRecord{" +
                "name='" + name + '\'' +
                ", type=" + String.format("%x", type) +
                ", rClass=" + String.format("%x", rClass) +
                ", ttl=" + String.format("%x", ttl) +
                ", expiry=" + String.format("%x", expiry) +
                ", rdlength=" + String.format("%x", rdlength) +
                ", rdata=" + Arrays.toString(rdata) +
                '}';
    }

//    boolean timestampValid(){
//        // return whether the creation date + the time to live is after the current time.
//        // The Date and Calendar classes will be useful for this.
//        return Instant.now().getEpochSecond() <= expiry;
//    }
}
