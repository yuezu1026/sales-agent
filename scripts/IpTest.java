import org.lionsoul.ip2region.xdb.*;
import java.io.*;
import java.nio.file.*;

public class IpTest {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("backend/src/main/resources/ip2region.xdb"));
        LongByteArray cbuf = Searcher.loadContentFromInputStream(new ByteArrayInputStream(bytes));
        Searcher s = Searcher.newWithBuffer(Version.IPv4, cbuf);
        String[] ips = {"114.114.114.114", "223.5.5.5", "180.101.50.188", "1.2.4.8", "117.136.0.1"};
        for (String ip : ips) {
            System.out.println(ip + " => " + s.search(ip));
        }
    }
}
