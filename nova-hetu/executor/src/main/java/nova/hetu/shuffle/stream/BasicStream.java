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
package nova.hetu.shuffle.stream;

import io.hetu.core.transport.execution.buffer.PagesSerde;
import io.hetu.core.transport.execution.buffer.SerializedPage;
import io.prestosql.spi.Page;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import static io.prestosql.spi.block.PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
import static nova.hetu.shuffle.stream.Constants.EOS;
import static nova.hetu.shuffle.stream.Constants.MAX_QUEUE_SIZE;
import static nova.hetu.shuffle.stream.PageSplitterUtil.splitPage;

/**
 * must be used in a the following way to ensure proper handling of releasing the resources
 * try (Out out = ShuffleService.getOutStream(task)) {
 * out.write(page);
 * }
 */
public class BasicStream
        implements Stream
{
    private static Logger log = Logger.getLogger(BasicStream.class);

    private final BlockingQueue<SerializedPage> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE /** shuffle.grpc.buffer_size_in_item */);
    private final Set<Integer> channels = new HashSet<>();
    private final Set<Integer> closedChannels = new HashSet<>();

    private final PagesSerde serde;
    private final String id;

    private boolean eos; // endOfStream
    private Consumer<Boolean> streamDestroyHandler;

    public BasicStream(String id, PagesSerde serde)
    {
        this.id = id;
        this.serde = serde;
        StreamManager.putIfAbsent(id, this);
    }

    @Override
    public SerializedPage take()
            throws InterruptedException
    {
        return queue.take();
    }

    @Override
    public SerializedPage take(int channelId)
            throws InterruptedException
    {
        SerializedPage page = take();
        if (page == EOS) {
            closedChannels.add(channelId);
            if (closedChannels.size() < channels.size()) {
                // Make sure all the channels can get EOS
                queue.put(EOS);
            }
        }
        return page;
    }

    /**
     * write out the page synchronously
     *
     * @param page
     */
    @Override
    public void write(Page page)
            throws InterruptedException
    {
        if (eos) {
            throw new IllegalStateException("Stream has already been closed");
        }
        for (Page splittedPage : splitPage(page, DEFAULT_MAX_PAGE_SIZE_IN_BYTES)) {
            SerializedPage serializedPage = serde.serialize(splittedPage);
            queue.put(serializedPage);
        }
    }

    @Override
    public void addChannels(List<Integer> channelIds, boolean noMoreChannels)
            throws InterruptedException
    {
        for (Integer channelId : channelIds) {
            if (channels.contains(channelId)) {
                continue;
            }

            String streamId = id + "-" + channelId;
            StreamManager.putIfAbsent(streamId, new ReferenceStream(channelId, streamId, this));
            channels.add(channelId);
            if (eos) {
                queue.put(EOS);
            }
        }
    }

    @Override
    public boolean isClosed()
    {
        return eos && queue.isEmpty();
    }

    @Override
    public void destroy()
    {
        StreamManager.remove(id);
        if (streamDestroyHandler != null) {
            streamDestroyHandler.accept(true);
        }
    }

    @Override
    public void destroy(int channelId)
    {
        channels.remove(channelId);
        if (channels.isEmpty()) {
            destroy();
        }
    }

    @Override
    public void onDestroyed(Consumer<Boolean> streamDestroyHandler)
    {
        this.streamDestroyHandler = streamDestroyHandler;
    }

    @Override
    public void close()
            throws InterruptedException
    {
        if (!eos) {
            eos = true;
            for (int i = 0; i < channels.size(); i++) {
                queue.put(EOS);
            }
        }
    }
}