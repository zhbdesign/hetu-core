/*
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
package io.prestosql.sql.tree;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;

public class DropIndex
        extends Statement
{
    private final QualifiedName indexName;
    private final boolean exists;

    public DropIndex(QualifiedName indexName, boolean exists)
    {
        this(Optional.empty(), indexName, exists);
    }

    public DropIndex(NodeLocation location, QualifiedName indexName, boolean exists)
    {
        this(Optional.of(location), indexName, exists);
    }

    private DropIndex(Optional<NodeLocation> location, QualifiedName indexName, boolean exists)
    {
        super(location);
        this.indexName = indexName;
        this.exists = exists;
    }

    public QualifiedName getIndexName()
    {
        return indexName;
    }

    public boolean isExists()
    {
        return exists;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitDropIndex(this, context);
    }

    @Override
    public List<Node> getChildren()
    {
        return ImmutableList.of();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(indexName, exists);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        DropIndex o = (DropIndex) obj;
        return Objects.equals(indexName, o.indexName)
                && (exists == o.exists);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("indexName", indexName)
                .add("exists", exists)
                .toString();
    }
}
