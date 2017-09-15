package com.icodici.universa.node;

import com.icodici.universa.node.network.HttpClient;
import net.sergeych.tools.Binder;
import net.sergeych.tools.ConsoleInterceptor;
import org.junit.Test;

import static com.icodici.universa.RegexMatcher.matches;
import static org.junit.Assert.*;

public class NodeStarterTest extends TestCase {


    // We need to fix shutting down instance - can't test after it...
    // java can;t for a process so we can't properly and timely shut it down
    @Test
    public void createNode() throws Exception {
        String result = ConsoleInterceptor.copyOut(()-> {
            Thread t = new Thread(() -> {
                NodeStarter.main(new String[]{"-c", "src/test_config2", "-i", "node7", "-p", "15510"});
            });
            t.start();
            NodeStarter.waitReady();
            HttpClient client = new HttpClient("test", "http://127.0.0.1:15510");
            HttpClient.Answer answer = client.request("network");
            assertTrue(answer.isOk());
//        System.out.println(answer.data);
            Binder n4 = answer.data.getBinderOrThrow("node4");
            assertEquals(2086, n4.getIntOrThrow("port"));
            assertEquals("127.0.0.1", n4.getStringOrThrow("ip"));
            NodeStarter.shutdown();
        });
        Thread.sleep(300);
        assertThat(result, matches(".*Universa.*"));
        System.out.println( result);
    }
}