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

import io.hetu.core.transport.execution.buffer.SerializedPage;
import io.prestosql.spi.Page;

import java.util.List;
import java.util.function.Consumer;

public interface Stream
        extends AutoCloseable
{
    enum Type
    {
        BASIC,
        BROADCAST,
    }

    SerializedPage take()
            throws InterruptedException;

    SerializedPage take(int channelId)
            throws InterruptedException;

    /**
     * write out the page synchronously
     *
     * @param page
     */
    void write(Page page)
            throws InterruptedException;

    void addChannels(List<Integer> channelIds, boolean noMoreChannels)
            throws InterruptedException;

    boolean isClosed();

    void destroy();

    void destroy(int channelId);

    void onDestroyed(Consumer<Boolean> streamDestroyHandler);
}