import java.security.SecureRandom;

public class SecureRandomTest {
    public static void main(String[] args) {
        try {
            SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
            int total = 0;
            int step = 32*500;
            while (total < step*100) {
                long t = System.currentTimeMillis();
                byte[] keys = new byte[step];
                rng.nextBytes(keys);
                total += step;
                t = System.currentTimeMillis() - t;
                System.out.println("" + total + ": " + t + "ms");
            }
        }
        catch( Exception e) {
            e.printStackTrace();
        }
    }
}
