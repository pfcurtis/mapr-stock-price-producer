/* Copyright (c) 2009 & onwards. MapR Tech, Inc., All rights reserved */
package com.mapr.demo.stock_ticker;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class ConsumerForTest {

    // Declare a new consumer.
    public static KafkaConsumer consumer;

    public static void main(String[] args) throws IOException {
        configureConsumer(args);

        String topic = "/user/user01/pump:sensor";
        if (args.length == 1) {
            topic = args[0];
        }

        List<String> topics = new ArrayList<String>();
        topics.add(topic);
        // Subscribe to the topic.
        consumer.subscribe(topics);

        // Set the timeout interval for requests for unread messages.
        long pollTimeOut = 60000;     // one minute
        long numberOfMsgsReceived = 0;
        while (true) {
            // Request unread messages from the topic.
            ConsumerRecords<String, String> msg = consumer.poll(pollTimeOut);
            if (msg.count() == 0) {
                System.out.println("No messages after 60 second wait.");
                break;
            } else {
                System.out.println("Read " + msg.count() + " messages");
                numberOfMsgsReceived += msg.count();

                // Iterate through returned records, extract the value
                // of each message, and print the value to standard output.
                Iterator<ConsumerRecord<String, String>> iter = msg.iterator();
                while (iter.hasNext()) {
                    ConsumerRecord<String, String> record = iter.next();
                    System.out.println("Consuming " + record.toString());

                }
            }
        }
        consumer.close();
        System.out.println("Total number of messages received: " + numberOfMsgsReceived);
        System.out.println("All done.");

    }

    /* Set the value for configuration parameters.*/
    public static void configureConsumer(String[] args) {
        Properties props = new Properties();
        // cause consumers to start at beginning of topic on first read
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        //  which class to use to deserialize the value of each message
        props.put("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<String, String>(props);
    }

}
