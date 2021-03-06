package com.mapr.demo.stock_ticker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.Exception;
import java.lang.Thread;

import com.google.common.base.Charsets;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer {

    private static final Logger LOG = LoggerFactory.getLogger(Producer.class);

    private static KafkaProducer producer;
    private static final LinkedList<File> DATAFILES = new LinkedList<>();
    private static final AtomicLong PROCESSED = new AtomicLong();
    private final String topic;
    private static String stats;
    private final File taqFile;
    private int current, last = 0;

    public Producer(final String topic, final File f) {
        this.taqFile = f;
        this.topic = topic;
        this.stats = topic + "_stats";
        configureProducer();
        if (taqFile.isDirectory()) {
            for (final File fileEntry : taqFile.listFiles()) {
                if (fileEntry.isDirectory()) {
                    System.err.println("WARNING: skipping files in directory " + fileEntry.getName());
                } else {
                    DATAFILES.add(fileEntry);
                }
            }
        } else {
            DATAFILES.add(taqFile);
        }
    }

    public void produce() throws IOException {
        System.out.println("Publishing data from " + DATAFILES.size() + " files.");
        long startTime = System.nanoTime();
        long last_update = 0;

        for (final File f : DATAFILES) {
            FileReader fr = new FileReader(f);
            BufferedReader reader = new BufferedReader(fr);
            String line = reader.readLine();
            int interval = 0;

            try {
                while (line != null) {
                    long current_time = System.nanoTime();
		    String key = line.substring(0, 16);
                    ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, line.getBytes(Charsets.ISO_8859_1));

                    producer.send(record, (RecordMetadata metadata, Exception e) -> {
                        PROCESSED.incrementAndGet();
                    });

                    current = Integer.parseInt(line.substring(6, 9));
                    if ((current - last) < 0)
                        interval = (current + 1000) - last;
                    else
                        interval = current - last;
                    Thread.sleep(interval);
                    last = current;
                    // Print performance stats once per second
                    if ((Math.floor(current_time - startTime) / 1e9) > last_update) {
                        last_update++;
                        producer.flush();
                        printStatus(PROCESSED.get(), 1, startTime);
                    }
                    line = reader.readLine();
                }

            } catch (Exception e) {
                System.err.println("ERROR: " + e);
                System.err.println("Line :'"+line+"'");
                e.printStackTrace();
            }
        }
        producer.flush();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        System.out.println("Published " + PROCESSED + " messages to stream.");
        System.out.println("Finished.");
        producer.close();
    }

    /**
     * Set the value for a configuration parameter. This configuration parameter specifies which class to use to
     * serialize the value of each message.
     */
    public static void configureProducer() {
        Properties props = new Properties();
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        producer = new KafkaProducer<>(props);
    }

    public static void printStatus(long records_processed, int poolSize, long startTime) {
        long elapsedTime = System.nanoTime() - startTime;
        double rp = records_processed / ((double) elapsedTime / 1000000000.0) / 1000;
        System.out.printf("Throughput = %.2f Kmsgs/sec published. Threads = %d. Total published = %d.\n",
                          rp,
                          poolSize,
                          records_processed);
        ProducerRecord<String, byte[]> stats_event = new ProducerRecord<>(stats, Long.toString(elapsedTime / 1000), 
            new Double(rp).toString().getBytes(Charsets.ISO_8859_1));
                    producer.send(stats_event);
    }

    public static void main(String[] args) throws IOException, Exception {
        if (args.length != 2) {
            throw new Exception("Usage: java -cp target/stock-ticker-1.0.jar stream:topic [file name | directory]");
        }
        Producer p = new Producer(args[0], new File(args[1]));
        try {
            p.produce();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
