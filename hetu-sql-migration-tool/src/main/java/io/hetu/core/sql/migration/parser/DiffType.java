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
package io.hetu.core.sql.migration.parser;

public enum DiffType
{
    DELETED(0, "deleted"),
    MODIFIED(1, "modified"),
    INSERTED(2, "inserted"),
    FUNCTION_WARNING(3, "warning"),
    UNSUPPORTED(4, "unsupported");

    DiffType(int code, String value)
    {
        this.code = code;
        this.value = value;
    }

    public int getCode()
    {
        return code;
    }

    public String getValue()
    {
        return value;
    }

    private int code;
    private String value;
}
