/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.modeler.modules;

import java.util.List;

import javax.management.ObjectName;

import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * 建模资源类
 */
public abstract class ModelerSource {
    protected static final StringManager sm = StringManager.getManager(Registry.class);

    /**
     * url资源对象
     */
    protected Object source;

    /**
     * 从url资源中加载描述器
     * @param registry 登记器对象
     * @param type 类型
     * @param source url资源对象
     * @return
     * @throws Exception
     */
    public abstract List<ObjectName> loadDescriptors(Registry registry,
            String type, Object source) throws Exception;
}
