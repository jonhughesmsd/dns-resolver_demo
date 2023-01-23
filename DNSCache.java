import java.time.Instant;
import java.util.HashMap;

public class DNSCache {
    static public HashMap<DNSQuestion, DNSRecord> hm = new HashMap<>();

    static boolean inCache(DNSMessage msg){

        if (hm.containsKey(msg.questions.get(0))){
            DNSRecord record = hm.get(msg.questions.get(0));
            if (Instant.now().getEpochSecond() <= record.expiry){
                return true;
            }
            else {
                hm.remove(msg.questions.get(0));
            }
        }
        return false;
    }

    static DNSRecord getAnswer(DNSMessage msg){
        DNSRecord ans = hm.get(msg.questions.get(0));
        long elaspedTime = Instant.now().getEpochSecond() - ans.timestamp;
        ans.ttl = (int) (ans.ttl - elaspedTime);        // assuming difference will never exceed int MAX_VALUE
        return ans;
    }

    static boolean addAnswer(DNSMessage msg){
        hm.put(msg.questions.get(0), msg.answers.get(0));
        return hm.containsKey(msg.questions.get(0));        // check question/answer added successfully
    }

}
