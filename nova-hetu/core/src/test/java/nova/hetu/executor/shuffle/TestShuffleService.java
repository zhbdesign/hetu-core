/*
 * Copyright (C) 2018-2020. Huawei Technologies Co., Ltd. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nova.hetu.executor.shuffle;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.SliceInput;
import io.airlift.slice.SliceOutput;
import io.hetu.core.transport.execution.buffer.PagesSerde;
import io.hetu.core.transport.execution.buffer.SerializedPage;
import io.prestosql.spi.Page;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockEncodingSerde;
import io.prestosql.spi.block.LongArrayBlock;
import nova.hetu.GrpcServer;
import nova.hetu.RsServer;
import nova.hetu.ShuffleServiceConfig;
import nova.hetu.shuffle.PageConsumer;
import nova.hetu.shuffle.PageProducer;
import nova.hetu.shuffle.ProducerInfo;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static nova.hetu.shuffle.stream.Stream.Type.BASIC;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestShuffleService
{
    private static Random random = new Random();

    @BeforeSuite
    public void setup()
            throws InterruptedException
    {
        RsServer.start(new ShuffleServiceConfig());
        GrpcServer.start();
    }

    @AfterSuite
    public void tearDown()
    {
        GrpcServer.shutdown();
    }

    @Test
    public void TestSingleProducerSingleConsumerQueue()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;

        PagesSerde serde = new MockConstantPagesSerde();

        PageProducer producer = new PageProducer(taskid, serde, BASIC);
        producer.addConsumers(ImmutableList.of(bufferid), true);
        Thread producerThread = createProducerThread(producer, 0, 10, 100);
        producerThread.start();
        producerThread.join();

        producer.close();

        long[] result = new long[10];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 100);
        consumerThread.start();
        consumerThread.join();

        //ensure all results received
        for (int i = 0; i < 10; i++) {
            assertEquals(i, result[i]);
        }
        assertEquals(true, consumer.isEnded());
    }

    @Test
    public void TestNoOutputProducer()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;
        PagesSerde serde = new MockConstantPagesSerde();
        PageProducer producer = new PageProducer(taskid, serde, BASIC);
        producer.addConsumers(ImmutableList.of(bufferid), true);
        producer.close();

        long[] result = new long[0];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 0);
        consumerThread.start();
        consumerThread.join();
        assertEquals(true, consumer.isEnded());
    }

    public void TestSingleProducerMultipleConsumerQueue()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;

        PagesSerde serde = new MockConstantPagesSerde();

        PageProducer producer = new PageProducer(taskid, serde, BASIC);
        producer.addConsumers(ImmutableList.of(bufferid), true);
        Thread producerThread = createProducerThread(producer, 0, 10, 100);
        producerThread.start();
//        PageProducer producer2 = PageProducer.create(taskid, bufferid, serde);
//        Thread producerThread2 = createProducerThread(producer2, 0, 10, 100);
//        producerThread2.start();
//        producerThread2.join();
        producerThread.join();

        producer.close();

        long[] result = new long[10];
        long[] result2 = new long[10];

        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        PageConsumer consumer2 = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 100);
        Thread consumerThread2 = createConsumerThread(consumer2, result2, 100);
        consumerThread.start();
        consumerThread2.start();

        consumerThread.join();
        consumerThread2.join();

        //ensure all results received
        for (int i = 0; i < 10; i++) {
            assertEquals(i, result[i]);
        }
        //ensure all results received
        for (int i = 0; i < 10; i++) {
            assertEquals(i, result2[i]);
        }
        assertEquals(true, consumer.isEnded());
        assertEquals(true, consumer2.isEnded());
    }

    @Test
    public void TestMultiProducerSingleConsumerWithDelayQueue()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;
        PagesSerde serde = new MockConstantPagesSerde();

        long[] result = new long[40];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 100);

        PageProducer producer1 = new PageProducer(taskid, serde, BASIC);
        PageProducer producer2 = new PageProducer(taskid, serde, BASIC);
        producer1.addConsumers(ImmutableList.of(bufferid), true);

        Thread producer1Thread = createProducerThread(producer1, 0, 20, 100);
        Thread producer2Thread = createProducerThread(producer2, 20, 40, 100);

        producer1Thread.start();
        producer2Thread.start();
        consumerThread.start();

        producer1Thread.join();
        producer2Thread.join(); //wait for all producers to finish before exiting the try which closes the Out;

        producer1.close();
        producer2.close();

        consumerThread.join();

        while (!consumer.isEnded()) {
            Thread.sleep(50);
        }
        if (consumer.isEnded()) {
            //ensure all results received
            for (int i = 0; i < 40; i++) {
                assertEquals(i, result[i]);
            }
        }
        assertEquals(true, consumer.isEnded());
    }

    @Test
    public void TestMultiProducerSingleConsumerNoDelay()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;
        PagesSerde serde = new MockConstantPagesSerde();

        long[] result = new long[40];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);

        assertFalse(consumer.isEnded(), "page consumer started with ended status");

        Thread consumerThread = createConsumerThread(consumer, result, 100);

        PageProducer producer1 = new PageProducer(taskid + "-" + bufferid, serde, BASIC);
        PageProducer producer2 = new PageProducer(taskid + "-" + bufferid, serde, BASIC);
        producer1.addConsumers(ImmutableList.of(bufferid), true);

        Thread producer1Thread = createProducerThread(producer1, 0, 20, 100);
        Thread producer2Thread = createProducerThread(producer2, 20, 40, 100);

        producer1Thread.start();
        producer2Thread.start();
        consumerThread.start();

        producer1Thread.join();
        producer2Thread.join(); //wait for all producers to finish before exiting the try which closes the Out;

        producer1.close();
        producer2.close();

        consumerThread.join();

        while (!consumer.isEnded()) {
            Thread.sleep(50);
        }
        if (consumer.isEnded()) {
            //ensure all results received
            for (int i = 0; i < 40; i++) {
                assertEquals(i, result[i]);
            }
        }
        assertEquals(true, consumer.isEnded());
    }

    @Test
    public void TestMultiProducerMultiConsumerNoDelay()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferId1 = 1;
        int bufferId2 = 2;
        PagesSerde serde = new MockConstantPagesSerde();

        long[] result = new long[400];
        PageConsumer consumer1 = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferId1), serde);
        PageConsumer consumer2 = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferId2), serde);

        assertFalse(consumer1.isEnded(), "page consumer started with ended status");
        assertFalse(consumer2.isEnded(), "page consumer started with ended status");

        PageProducer producer1 = new PageProducer(taskid, serde, BASIC);
        PageProducer producer2 = new PageProducer(taskid, serde, BASIC);
        producer1.addConsumers(ImmutableList.of(bufferId1, bufferId2), true);

        Thread consumerThread1 = createConsumerThread(consumer1, result, 100);
        Thread consumerThread2 = createConsumerThread(consumer2, result, 100);

        Thread producer1Thread = createProducerThread(producer1, 0, 200, 100);
        Thread producer2Thread = createProducerThread(producer2, 200, 400, 100);

        producer1Thread.start();
        producer2Thread.start();
        consumerThread1.start();
        consumerThread2.start();

        producer1Thread.join();
        producer2Thread.join(); //wait for all producers to finish before exiting the try which closes the Out;

        producer1.close();
        producer2.close();

        consumerThread1.join();
        consumerThread2.join();

        while (!consumer1.isEnded() || !consumer2.isEnded()) {
            Thread.sleep(50);
        }
        //ensure all results received
        for (int i = 0; i < result.length; i++) {
            assertEquals(i, result[i]);
        }
        assertEquals(true, consumer1.isEnded());
        assertEquals(true, consumer2.isEnded());
    }

    public void TestMultiProducerSingleConsumerQueue()
            throws Exception
    {
        String taskid = getTaskId();
        int bufferid = 0;

        PagesSerde serde = new MockConstantPagesSerde();
        long[] result = new long[4000];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 0);

        PageProducer producer1 = new PageProducer(taskid, serde, BASIC);
        PageProducer producer2 = new PageProducer(taskid, serde, BASIC);
        PageProducer producer3 = new PageProducer(taskid, serde, BASIC);
        PageProducer producer4 = new PageProducer(taskid, serde, BASIC);
        producer1.addConsumers(ImmutableList.of(bufferid), true);

        Thread producer1Thread = createProducerThread(producer1, 0, 1000, 0);
        Thread producer2Thread = createProducerThread(producer2, 1000, 2000, 0);
        Thread producer3Thread = createProducerThread(producer3, 2000, 3000, 0);
        Thread producer4Thread = createProducerThread(producer4, 3000, 4000, 0);

        producer1Thread.start();
        producer2Thread.start();
        producer3Thread.start();
        producer4Thread.start();

        consumerThread.start();

        producer1Thread.join();
        producer2Thread.join();
        producer3Thread.join();
        producer4Thread.join();

        producer1.close();
        producer2.close();
        producer3.close();
        producer4.close();

        consumerThread.join();

        while (!consumer.isEnded()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }

        //ensure all results received
        for (int i = 0; i < 4000; i++) {
            assertEquals(i, result[i]);
        }
        assertTrue(consumer.isEnded());
    }

    @Test
    void TestPageConsumerStatus()
            throws InterruptedException
    {
        String taskid = getTaskId();
        String bufferid = "0";

        PagesSerde serde = new MockConstantPagesSerde();
        long[] result = new long[4000];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 0);

        assertFalse(consumer.isEnded(), "Consumer should not start with ended status");
        TimeUnit.MILLISECONDS.sleep(500);
        assertFalse(consumer.isEnded(), "Consumer should not be ended without getting any input");

        //potential half close situation when producer never published anything, not even EOS?? is this a legal problem?
    }

    @Test
    void TestConcurrentPageExchanges()
            throws ExecutionException, InterruptedException
    {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future> results = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            Future future = executorService.submit(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("TestMultiProducerSingleConsumerQueue");
                    TestMultiProducerSingleConsumerQueue();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    Thread.currentThread().setName(threadName);
                }
            });
            results.add(future);
        }
        for (Future result : results) {
            result.get();
        }
        executorService.shutdown();
    }

    @Test
    public void TestConsumerStatus()
            throws Exception
    {
        GrpcServer.start();
        String taskid = getTaskId();
        int bufferid = 0;
        PagesSerde serde = new MockConstantPagesSerde();
        PageProducer producer = new PageProducer(taskid + "-" + bufferid, serde, BASIC);
        producer.addConsumers(ImmutableList.of(bufferid), true);

        Thread producerThread = createProducerThread(producer, 0, 10, 100);
        producerThread.start();
        producerThread.join();

        producer.close();

        long[] result = new long[10];
        PageConsumer consumer = PageConsumer.create(new ProducerInfo("127.0.0.1", 16544, taskid + "-" + bufferid), serde);
        Thread consumerThread = createConsumerThread(consumer, result, 0);
        consumerThread.start();
        consumerThread.join();
        //ensure all results received
        for (int i = 0; i < 10; i++) {
            assertEquals(i, result[i]);
        }
        assertEquals(true, consumer.isEnded());
    }

    static Thread createConsumerThread(PageConsumer consumer, long[] result, int delay)
    {
        return new Thread(() -> {
            try {
                Thread.currentThread().setName("consumer thread");
                int count = 0;
                while (!consumer.isEnded()) {
                    Page page = consumer.poll();
                    if (page == null) {
                        continue;
                    }
                    count++;
                    int value = (int) page.getBlock(0).getLong(0, 0);
                    assertEquals(0, result[value], "received duplicate page");
                    result[value] = value;
                    System.out.println(consumer.toString() + " taking the output: " + page + " content:" + page.getBlock(0).getLong(0, 0));
                    if (delay > 0) {
                        Thread.sleep(random.nextInt(delay));
                    }
                }
                System.out.println("taking the output ended, :" + count);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * The test assumes the page value will be int and the values[idx] = idx;
     * This is to simplify the verification of the transport layer correctness
     *
     * @param producer
     * @param start start value
     * @param end end value
     * @param delay amount of delay between sending pages
     * @return
     */
    static Thread createProducerThread(PageProducer producer, int start, int end, int delay)
    {
        return new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    System.out.println("Producing pages from " + start + " to " + end + " with delay " + delay);
                    for (int i = start; i < end; i++) {
                        System.out.println("sedning: " + i);
                        producer.send(ShuffleServiceTestUtil.getPage(i));
                        if (delay > 0) {
                            Thread.sleep(random.nextInt(delay));
                        }
                    }
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        });
    }

    static String getTaskId()
    {
        return "task-" + random.nextInt(10000);
    }

    static class MockConstantPagesSerde
            extends PagesSerde
    {
        MockConstantPagesSerde()
        {
            super(new MockBlockEncodingSerde(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        @Override
        public SerializedPage serialize(Page page)
        {
            String strValue = String.valueOf(page.getBlock(0).getLong(0, 0));
            return new SerializedPage(strValue.getBytes(), (byte) 0, page.getPositionCount(), strValue.getBytes().length);
        }

        @Override
        public Page deserialize(SerializedPage serializedPage)
        {
            serializedPage.getPositionCount();
            long[] values = new long[] {Long.valueOf(new String(serializedPage.getSliceArray()))};
            LongArrayBlock block = new LongArrayBlock(1, Optional.empty(), values);
            return new Page(block);
        }
    }

    static class MockBlockEncodingSerde
            implements BlockEncodingSerde
    {
        @Override
        public Block readBlock(SliceInput input)
        {
            return null;
        }

        @Override
        public void writeBlock(SliceOutput output, Block block)
        {
        }
    }
}