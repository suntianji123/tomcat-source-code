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


package org.apache.catalina.startup;


import java.lang.reflect.Method;

import org.apache.catalina.Container;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * 复制父类标签对象类加载器规则
 */
public class CopyParentClassLoaderRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new instance of this Rule.
     */
    public CopyParentClassLoaderRule() {
    }


    // --------------------------------------------------------- Public Methods


    /**
     *  开始解析标签
     * @param namespace the namespace URI of the matching element, or an
     * @param name the local name if the parser is namespace aware, or just
     * @param attributes The attribute list of this element
     * @throws Exception
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("Copying parent class loader");
        //获取当前标签对象 比如StandardHost对象
        Container child = (Container) digester.peek(0);
        //获取父类标签对象 比如StandardEnginer对象
        Object parent = digester.peek(1);

        //获取父类方法 getParentClassLoader
        Method method =
            parent.getClass().getMethod("getParentClassLoader", new Class[0]);
        //执行父标签的getParentClassLoader方法 获取父标签对象class类加载器
        ClassLoader classLoader =
            (ClassLoader) method.invoke(parent, new Object[0]);
        //设置当前标签对象的类加载器
        child.setParentClassLoader(classLoader);

    }


}
