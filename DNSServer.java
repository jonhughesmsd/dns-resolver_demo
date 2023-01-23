import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.Arrays;


public class DNSServer {

    static Inet4Address queryAddress;
    static int queryPort;

    /**
     * This class opens a UDP socket (DatagramSocket) and listens for requests on Port 8053.
     * When it gets on, it looks at the question (Assumption: there is only one) in the
     * request. If there is a valid answer in the cache, add it to the response. Otherwise,
     * forward the request to Google (8.8.8.8) on Port 53 and then await the response, cache
     * the answer from Google (if there is one) and forward Google's response to the client.
     * @param args
     * @throws IOException for errors in reading from or writing to byte array streams
     */
    public static void main(String[] args) throws IOException {
        boolean done = false;

        // Test Socket
        try{
            DatagramSocket testSocket = new DatagramSocket(8053);
            testSocket.close();
        }
        catch(Exception e){
            System.out.println("Could not open socket:8053");
            done = true;
        }

        // Initialize Socket and DatagramPacket
        DatagramSocket dgSocket = new DatagramSocket(8053);
        byte[] receive = new byte[1500];     // IPv4 standard must reassemble packets of 576 bytes
                                            // 1500 seems to be the effective MTU across the internet
        DatagramPacket dgPacket = null;

        while (!done) {     // forever loop
            // based on: https://www.geeksforgeeks.org/working-udp-datagramsockets-java/

            // create a DatgramPacket to receive the data
            dgPacket = new DatagramPacket(receive, receive.length);

            // receive the data in byte buffer
            dgSocket.receive(dgPacket);
            byte[] receivedData = dgPacket.getData();     // returns a byte array
            int queryLength = dgPacket.getLength();
            queryAddress = (Inet4Address) dgPacket.getAddress();    // save query address and port number for response
            queryPort = dgPacket.getPort();

            // print out the contents of the byte array
//            System.out.println("Query Array: " + Arrays.toString(receivedData));

            // decode dns query
            DNSMessage msg = DNSMessage.decodeMessage(receivedData);

            // check the cache
            boolean inCache = DNSCache.inCache(msg);

            if (!inCache) {
                // if not in cache, ask google
                // build datagram packet with google's dns 8.8.8.8 and port 53
                Inet4Address google = (Inet4Address) Inet4Address.getByName("8.8.8.8");
                DatagramPacket googlePacket = new DatagramPacket(receivedData, queryLength, google, 53);
                dgSocket.send(googlePacket);
//                System.out.println("google packet sent");

                // receive the response from Google and store in a separate byte array
                byte[] googleReceive = new byte[1500];
                DatagramPacket googleResponsePacket = new DatagramPacket(googleReceive, googleReceive.length);
                dgSocket.receive(googleResponsePacket);
                byte[] googleData = googleResponsePacket.getData();
                int googleDataLength = googleResponsePacket.getLength();

                // print out the contents of Google's response
//                System.out.println("Google Array: " + Arrays.toString(googleData));

                // decode response from google
                DNSMessage googleMsg = DNSMessage.decodeMessage(googleData);

                if (googleMsg.header.qr) {                      // check that qr bit is set

                    // Check that there are Answers in Google's response
                    // if no answers, nothing to store in cache (also, DNS host not found)
                    // if addAnswer returns false, error adding to Cache
                    if (googleMsg.header.ancount > 0 && (!DNSCache.addAnswer(googleMsg))){        // add google answer to cache
                        System.exit(-1);
                    }

                    // Forward Google's response to the client
                    DatagramPacket responsePacket = new DatagramPacket(googleData, googleDataLength, queryAddress, queryPort);
                    dgSocket.send(responsePacket);
//                    System.out.println("Google response packet sent to client\n\n");
                }
                else {
                    System.exit(-1);                        // qr bit in response is 0
                }
            }
            else {
                DNSRecord answer = DNSCache.getAnswer(msg);     // get cache answer
//                System.out.println("Cached response!\n");

                // Build packet with Answer from Cache and send to client
                DNSMessage response = DNSMessage.buildResponse(msg, answer);
                byte[] responseData = response.toBytes(response);

                // print out the contents of the built byte array
//                System.out.println("Response Array: " + Arrays.toString(responseData));

                // send response packet to client
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, queryAddress, queryPort);
                dgSocket.send(responsePacket);
//                System.out.println("Cached response packet sent to client\n\n");
            }

        }
    }
}
