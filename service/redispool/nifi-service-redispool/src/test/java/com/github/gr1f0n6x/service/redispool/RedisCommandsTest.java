package com.github.gr1f0n6x.service.redispool;

import com.github.gr1f0n6x.service.common.Deserializer;
import com.github.gr1f0n6x.service.common.Serializer;
import com.github.gr1f0n6x.service.common.StringDeserializer;
import com.github.gr1f0n6x.service.common.StringSerializer;
import com.github.gr1f0n6x.service.redispool.service.RedisCommandsService;
import com.github.gr1f0n6x.service.redispool.service.RedisPoolService;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.embedded.RedisServer;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.*;

public class RedisCommandsTest {
    private TestRunner runner;
    private RedisServer server;
    private int port;


    @Before
    public void init() throws IOException {
        runner = TestRunners.newTestRunner(Processor.class);
        port = getAvailablePort();
        server = new RedisServer(port);
        server.start();
    }

    private int getAvailablePort() throws IOException {
        try (SocketChannel socket = SocketChannel.open()) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            socket.bind(new InetSocketAddress("localhost", 0));
            return socket.socket().getLocalPort();
        }
    }

    @After
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testCommands() {
        RedisPoolService pool = null;
        RedisCommandsService commands = null;
        try {
            pool = new RedisPoolService();
            commands = new RedisCommandsService();

            runner.addControllerService("redis-pool", pool);
            runner.setProperty(pool, RedisPoolService.HOST, "localhost");
            runner.setProperty(pool, RedisPoolService.PORT, String.valueOf(port));
            runner.enableControllerService(pool);

            runner.addControllerService("redis-commands", commands);
            runner.setProperty(commands, RedisCommandsService.REDIS_CONNECTION_POOL, "redis-pool");
            runner.enableControllerService(commands);

            runner.setProperty(Processor.REDIS_COMMANDS, "redis-commands");
            runner.enqueue("data");
            runner.run();

        } catch (InitializationException e) {
            e.printStackTrace();
        } finally {
            if (pool != null) {
                pool.disable();
            }
        }
    }

    public static class Processor extends AbstractProcessor {
        public static final PropertyDescriptor REDIS_COMMANDS = new PropertyDescriptor.Builder()
                .name("Redis commands")
                .identifiesControllerService(RedisCommands.class)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .required(true)
                .build();

        public static final Relationship SUCCESS = new Relationship.Builder().name("success").build();
        public static final Relationship FAILURE = new Relationship.Builder().name("failure").build();

        @Override
        protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
            return Collections.singletonList(REDIS_COMMANDS);
        }

        @Override
        public Set<Relationship> getRelationships() {
            return new HashSet<>(Arrays.asList(SUCCESS, FAILURE));
        }

        @Override
        public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
            final FlowFile flowFile = session.get();
            if (flowFile == null) {
                return;
            }

            final Serializer<String> stringSerializer = new StringSerializer();
            final Deserializer<String> stringDeserializer = new StringDeserializer();

            final RedisCommands cacheClient = context.getProperty(REDIS_COMMANDS).asControllerService(RedisCommands.class);


            try {
                String key = "key";
                String value = "value";

                assertFalse(cacheClient.exists(key, stringSerializer));
                cacheClient.set(key, value, stringSerializer, stringSerializer);
                assertTrue(cacheClient.exists(key, stringSerializer));
                String result = cacheClient.get(key, stringSerializer, stringDeserializer);
                assertEquals(value, result);
                cacheClient.delete(key, stringSerializer);
                assertFalse(cacheClient.exists(key, stringSerializer));

                session.transfer(flowFile, SUCCESS);
            } catch (Exception e) {
                e.printStackTrace();
                session.transfer(flowFile, FAILURE);
            }

        }
    }
}