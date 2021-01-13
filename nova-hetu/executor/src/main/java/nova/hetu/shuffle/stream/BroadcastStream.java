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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.prestosql.spi.block.PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
import static nova.hetu.shuffle.stream.Constants.EOS;
import static nova.hetu.shuffle.stream.Constants.MAX_QUEUE_SIZE;
import static nova.hetu.shuffle.stream.PageSplitterUtil.splitPage;

public class BroadcastStream
        implements Stream
{
    private final BlockingQueue<SerializedPage> initialPages = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final Map<Integer, BlockingQueue<SerializedPage>> channels = new HashMap<>();

    private static final Logger log = Logger.getLogger(BroadcastStream.class);

    private final PagesSerde serde;
    private final String id;
    private boolean eos; // endOfStream

    private boolean channelsAdded;
    private Consumer<Boolean> streamDestroyHandler;

    public BroadcastStream(String id, PagesSerde serde)
    {
        this.id = id;
        this.serde = serde;
    }

    @Override
    public SerializedPage take()
    {
        throw new RuntimeException("Channel id is required for Broadcast stream");
    }

    @Override
    public SerializedPage take(int channelId)
            throws InterruptedException
    {
        BlockingQueue<SerializedPage> channel = channels.get(channelId);
        if (channel == null) {
            throw new RuntimeException("Channel doesn't exist, stream id " + id + ", channel " + channelId);
        }
        log.info("Stream " + id + " take channel " + channelId);
        return channel.take();
    }

    @Override
    public void write(Page page)
            throws InterruptedException
    {
        List<SerializedPage> serializedPages = splitPage(page, DEFAULT_MAX_PAGE_SIZE_IN_BYTES).stream()
                .map(serde::serialize)
                .collect(Collectors.toList());

        if (!channelsAdded) {
            for (SerializedPage splittedPage : serializedPages) {
                initialPages.put(splittedPage);
                log.info("Stream " + id + " write initial pages " + page);
            }
            return;
        }

        for (BlockingQueue<SerializedPage> channel : channels.values()) {
            for (SerializedPage splittedPage : serializedPages) {
                channel.put(splittedPage);
                log.info("Stream " + id + " write channel " + channel.toString() + " page " + page);
            }
        }
    }

    @Override
    public void addChannels(List<Integer> channelIds, boolean noMoreChannels)
    {
        for (Integer channelId : channelIds) {
            if (channels.containsKey(channelId)) {
                continue;
            }

            String streamId = id + "-" + channelId;
            StreamManager.putIfAbsent(streamId, new ReferenceStream(channelId, streamId, this));
            channels.put(channelId, new LinkedBlockingQueue<>(MAX_QUEUE_SIZE));
            log.info("Stream " + id + " add channel " + channelId);
        }

        for (SerializedPage page : initialPages) {
            channels.values().forEach(channel -> {
                try {
                    channel.put(page);
                    log.info("Stream " + id + " add page to channels");
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        if (noMoreChannels) {
            channelsAdded = true;
            initialPages.clear();
        }
    }

    @Override
    public boolean isClosed()
    {
        return eos && initialPages.isEmpty() && channels.values().stream().allMatch(BlockingQueue::isEmpty);
    }

    @Override
    public void destroy()
    {
        log.info("Stream " + id + " destroyed");
        streamDestroyHandler.accept(true);
    }

    @Override
    public void destroy(int channelId)
    {
        channels.remove(channelId);
        log.info("Stream " + id + " channel " + channelId + " destroyed");
        if (channels.isEmpty() && streamDestroyHandler != null) {
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
            if (!channelsAdded) {
                initialPages.put(EOS);
                return;
            }

            for (BlockingQueue<SerializedPage> channel : channels.values()) {
                channel.put(EOS);
            }
            eos = true;
            log.info("Stream " + id + " closed");
        }
    }
}