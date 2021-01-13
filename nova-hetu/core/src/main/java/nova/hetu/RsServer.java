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
package nova.hetu;

import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.TcpServerTransport;
import nova.hetu.shuffle.rsocket.RsShuffleService;

public class RsServer
{
    private RsServer() {}

    public static void main(String[] args)
            throws InterruptedException
    {
        start(new ShuffleServiceConfig());
    }

    public static void start(ShuffleServiceConfig shuffleServiceConfig)
    {
        RSocketServer.create(new RsShuffleService())
                .payloadDecoder(PayloadDecoder.ZERO_COPY)
                .bind(TcpServerTransport.create(shuffleServiceConfig.getPort()))
                .block()
                .onClose();

        System.out.println("Server started");
    }

    public static void shutdown()
    {
        //do nothing for now
    }
}