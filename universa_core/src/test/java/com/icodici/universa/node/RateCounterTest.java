package com.icodici.universa.node;

import net.sergeych.tools.AsyncEvent;
import org.junit.Test;

import java.io.*;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class RateCounterTest {

    @Test
    public void immediatelyCounts() throws Exception {
        int limit = 5;
        int seconds = 1;
        RateCounter rc = new RateCounter(limit, Duration.ofSeconds(seconds));

        assertEquals(limit, rc.pulsesLeft());
        assertEquals(limit, rc.getPulseLimit());
        assertEquals(seconds, rc.getDuration().getSeconds());

        rc.countPulse(); // 1
        rc.countPulse(); // 2
        rc.countPulse(); // 3
        assertEquals(true, rc.countPulse()); // 4
        assertEquals(true, rc.countPulse()); // 5
        assertEquals(0, rc.pulsesLeft());
        assertEquals(false, rc.countPulse()); // 6
        assertEquals(-1, rc.pulsesLeft());
    }

    @Test
    public void timedCountsOneRoundPass() throws Exception {
        int limit = 5;
        int seconds = 1;
        RateCounter rc = new RateCounter(limit, Duration.ofSeconds(seconds));
        Timer timer = new Timer();

        AsyncEvent<Void> ae = new AsyncEvent<>();

        assertEquals(limit, rc.pulsesLeft());
        assertEquals(limit, rc.getPulseLimit());
        assertEquals(seconds, rc.getDuration().getSeconds());

        System.out.println("timedCountsOneRoundPass started");
        timer.scheduleAtFixedRate(new TimerTask() {

            private int counts = 0;
            private int maxCounts = 5;
            @Override
            public void run() {
                assertEquals(true, rc.countPulse());
                counts++;
                System.out.println("counts: " + counts);

                if(counts >= maxCounts) {
                    cancel();
                    ae.fire();
                }
            }
        }, 0, 100);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }
        assertEquals(0, rc.pulsesLeft());
        assertEquals(false, rc.countPulse()); // 6
        assertEquals(-1, rc.pulsesLeft());
    }

    @Test
    public void timedCountsTwoRoundsPass() throws Exception {
        int limit = 5;
        int seconds = 1;
        RateCounter rc = new RateCounter(limit, Duration.ofSeconds(seconds));
        Timer timer = new Timer();

        AsyncEvent<Void> ae = new AsyncEvent<>();

        assertEquals(limit, rc.pulsesLeft());
        assertEquals(limit, rc.getPulseLimit());
        assertEquals(seconds, rc.getDuration().getSeconds());

        System.out.println("timedCountsTwoRoundsPass started");
        timer.scheduleAtFixedRate(new TimerTask() {

            private int counts = 0;
            private int maxCounts = 8;
            @Override
            public void run() {
                assertEquals(true, rc.countPulse());
                counts++;
                System.out.println("counts: " + counts);
                if(counts >= maxCounts) {
                    cancel();
                    ae.fire();
                }
            }
        }, 0, 300);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }
        assertEquals(1, rc.pulsesLeft());
        assertEquals(true, rc.countPulse());
        assertEquals(false, rc.countPulse());
        assertEquals(-1, rc.pulsesLeft());
    }

    @Test
    public void timedCountsOneRoundNotPass() throws Exception {
        int limit = 5;
        int seconds = 1;
        RateCounter rc = new RateCounter(limit, Duration.ofSeconds(seconds));
        Timer timer = new Timer();

        AsyncEvent<Void> ae = new AsyncEvent<>();

        assertEquals(limit, rc.pulsesLeft());
        assertEquals(limit, rc.getPulseLimit());
        assertEquals(seconds, rc.getDuration().getSeconds());

        System.out.println("timedCountsOneRoundNotPass started");
        timer.scheduleAtFixedRate(new TimerTask() {

            private int counts = 0;
            private int maxCounts = 8;
            @Override
            public void run() {
                if(counts < limit)
                    assertEquals(true, rc.countPulse());
                else
                    assertEquals(false, rc.countPulse());
                counts++;
                System.out.println("counts: " + counts);

                if(counts >= maxCounts) {
                    cancel();
                    ae.fire();
                }
            }
        }, 0, 100);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }
        assertEquals(-3, rc.pulsesLeft());
        assertEquals(false, rc.countPulse());
    }

    @Test
    public void serializeTest() throws Exception {
        int limit = 5;
        int seconds = 1;
        RateCounter rc = new RateCounter(limit, Duration.ofSeconds(seconds));

        assertEquals(limit, rc.pulsesLeft());
        assertEquals(limit, rc.getPulseLimit());
        assertEquals(seconds, rc.getDuration().getSeconds());

        rc.countPulse(); // 1
        rc.countPulse(); // 2
        rc.countPulse(); // 3

        FileOutputStream fos = new FileOutputStream("temp.out");
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(rc);
        oos.flush();
        oos.close();

        // deserialize
        FileInputStream fis = new FileInputStream("temp.out");
        ObjectInputStream oin = new ObjectInputStream(fis);
        RateCounter rc2 = (RateCounter) oin.readObject();

        File file = new File("temp.out");
        if(file.exists()) file.delete();

        System.out.println(rc.millisecondsLeft() + " <-> " + rc2.millisecondsLeft());
        assertEquals(rc.getPulseLimit(), rc2.getPulseLimit());
        assertEquals(rc.getDuration().getSeconds(), rc2.getDuration().getSeconds());
        assertEquals(rc.pulsesLeft(), rc2.pulsesLeft());
        assertEquals(rc.millisecondsLeft(), rc2.millisecondsLeft());

        assertEquals(true, rc2.countPulse()); // 4
        assertEquals(true, rc2.countPulse()); // 5
        assertEquals(0, rc2.pulsesLeft());
        assertEquals(false, rc2.countPulse()); // 6
        assertEquals(-1, rc2.pulsesLeft());
    }

}