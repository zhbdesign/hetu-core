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
package nove.hetu.executor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.hetu.core.transport.execution.buffer.SerializedPage;
import nova.hetu.executor.ExecutorOuterClass;
import nova.hetu.executor.ShuffleGrpc;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

public class ShuffleClient
{
    public static ExecutorService executor = Executors.newFixedThreadPool(10);
    public static ManagedChannel channel;

    private static Logger log = Logger.getLogger(ShuffleClient.class);

    private ShuffleClient() {}

    /**
     * Get the execution results identified by a taskid and bufferid
     * The method runs in a separate thread. The SerializedPage is streamed back, streaming of pages terminates when there is not futher pages.
     *
     * @param host the host the task is running
     * @param port the port the task is listening
     * @param taskid identifier of the task
     * @param bufferid identifier of the partition of the task to be retrieved
     * @return
     */
    public static Future getResults(String host, int port, String taskid, String bufferid, LinkedBlockingDeque<SerializedPage> pageOutputBuffer)
    {
        return executor.submit(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    getResults();
                }
                catch (Exception e) {
                    log.error(e.getMessage());
                }
            }

            private void getResults()
            {
                //starts a gRpc service client
                //FIXME: should share channel
                synchronized (this) {
                    if (channel == null) {
                        channel = ManagedChannelBuilder
                                .forAddress(host, port)
                                .usePlaintext(/** FIXME: TLS disabled */)
                                .build();
                    }
                }
                ShuffleGrpc.ShuffleBlockingStub shuffler = ShuffleGrpc.newBlockingStub(channel);

                ExecutorOuterClass.Task task = ExecutorOuterClass.Task.newBuilder()
                        .setTaskId(taskid)
                        .setBufferId(bufferid)
                        .build();
                log.info("request " + task);

                /**
                 * get the result in a new thread, which should add the result into the pages buffer
                 */
                Iterator<ExecutorOuterClass.Page> pages = shuffler.getResult(task);

                while (pages.hasNext()) {
                    ExecutorOuterClass.Page page = pages.next();
                    log.info("retrieving next finished: " + page);

                    SerializedPage spage = new SerializedPage(page.getSliceArray().toByteArray(), (byte) page.getPageCodecMarkers(), page.getPositionCount(), page.getUncompressedSizeInBytes());
                    pageOutputBuffer.add(spage);
                    log.info("Add to pageOutputBuffer " + task + " page: " + page);
                }

                log.info("Received all pages for " + taskid + "-" + bufferid);
                //channel.shutdown();
            }
        });
    }
}
