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
package io.prestosql.heuristicindex;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.prestosql.filesystem.FileSystemClientManager;
import io.prestosql.spi.HetuConstant;
import io.prestosql.spi.connector.CreateIndexMetadata;
import io.prestosql.spi.filesystem.HetuFileSystemClient;
import io.prestosql.spi.heuristicindex.IndexClient;
import io.prestosql.spi.heuristicindex.IndexFactory;
import io.prestosql.spi.heuristicindex.IndexFilter;
import io.prestosql.spi.heuristicindex.IndexMetadata;
import io.prestosql.spi.heuristicindex.IndexWriter;
import io.prestosql.spi.service.PropertyService;
import io.prestosql.testing.NoOpIndexClient;
import io.prestosql.testing.NoOpIndexWriter;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class HeuristicIndexerManager
{
    private final FileSystemClientManager fileSystemClientManager;
    private static IndexFactory factory;
    private static final Logger LOG = Logger.get(HeuristicIndexerManager.class);

    private Path root;
    private HetuFileSystemClient fs;
    private IndexClient indexClient = new NoOpIndexClient();
    private IndexWriter indexWriter = new NoOpIndexWriter();

    public static HeuristicIndexerManager getNoOpHeuristicIndexerManager()
    {
        return new HeuristicIndexerManager(new FileSystemClientManager());
    }

    @Inject
    public HeuristicIndexerManager(FileSystemClientManager fileSystemClientManager)
    {
        this.fileSystemClientManager = fileSystemClientManager;
    }

    public void loadIndexFactories(IndexFactory indexFactory)
    {
        HeuristicIndexerManager.factory = indexFactory;
    }

    @VisibleForTesting
    protected static void checkFilesystemTimePrecision(String timeStamp)
            throws FileSystemException
    {
        if (!timeStamp.matches(".*?:\\d+:\\d+\\.\\d{3,}Z")) {
            throw new FileSystemException(String.format("The filesystem specified by hetu.heuristicindex.indexstore.filesystem.profile is not " +
                    "supported by Heuristic Index because the precision for Files.getLastModifiedTime(): %s is too low. " +
                    "Precision must at least be milliseconds.", timeStamp));
        }
    }

    public IndexClient getIndexClient()
    {
        return indexClient;
    }

    public IndexWriter getIndexWriter(CreateIndexMetadata createIndexMetadata, Properties connectorMetadata)
    {
        if (PropertyService.getBooleanProperty(HetuConstant.FILTER_ENABLED)) {
            if (factory != null) {
                return factory.getIndexWriter(createIndexMetadata, connectorMetadata, fs, root);
            }
        }

        return indexWriter;
    }

    public IndexFilter getIndexFilter(Map<String, List<IndexMetadata>> indices)
    {
        return factory.getIndexFilter(indices);
    }

    public void buildIndexClient()
            throws IOException
    {
        if (PropertyService.getBooleanProperty(HetuConstant.FILTER_ENABLED)) {
            String fsProfile = PropertyService.getStringProperty(HetuConstant.INDEXSTORE_FILESYSTEM_PROFILE);
            String indexStoreRoot = PropertyService.getStringProperty(HetuConstant.INDEXSTORE_URI);

            root = Paths.get(indexStoreRoot);
            fs = fileSystemClientManager.getFileSystemClient(fsProfile, root);
            if (fileSystemClientManager.isFileSystemLocal(fsProfile)) {
                LOG.warn("Profile %s is not a shared filesystem. It may not work properly if the cluster has more than 1 nodes.");
                String fileLastModifiedTimeSample = Files.getLastModifiedTime(Paths.get(System.getProperty("user.dir"))).toString();
                checkFilesystemTimePrecision(fileLastModifiedTimeSample);
            }
            if (factory != null) {
                indexClient = factory.getIndexClient(fs, root);
            }
        }
    }
}
