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


import org.apache.catalina.Container;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * 生命周期监听器规则类
 */
public class LifecycleListenerRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * 实例化一个生命周期监听器规则对象
     * @param listenerClass 监听class全类吗
     * @param attributeName 属性名
     */
    public LifecycleListenerRule(String listenerClass, String attributeName) {
        this.listenerClass = listenerClass;
        this.attributeName = attributeName;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 用户定义的容器对象的周期监听器的属性名 标签中这个属性值 将作为当前容器对象的生命周期监听器
     */
    private final String attributeName;


    /**
     * 当前标签容器对象默认的生命周期监听器
     */
    private final String listenerClass;


    // --------------------------------------------------------- Public Methods


    /**
     *开始解析标签
     * @param namespace 标签命名空间
     * @param name 标签名
     * @param attributes 属性列表
     * @throws Exception
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        //获取当前标签对象
        Container c = (Container) digester.peek();
        //父标签容器对象
        Container p = null;
        //获取父标签对象
        Object obj = digester.peek(1);
        if (obj instanceof Container) {//如果父标签对象为容器类型
            //当前容器对象
            p = (Container) obj;
        }

        //class类名
        String className = null;

        // Check the container for the specified attribute
        if (attributeName != null) {//如果指定了监听的属性名
            //获取属性值
            String value = attributes.getValue(attributeName);
            if (value != null)//属性值存在
                className = value;
        }


        if (p != null && className == null) {//如果当前标签是容器对象  并且用户没有指定属性名
            String configClass =
                (String) IntrospectionUtils.getProperty(p, attributeName);
            if (configClass != null && configClass.length() > 0) {
                className = configClass;
            }
        }

        // Use the default
        if (className == null) {//用户没有指定监听的class全类名 使用默认的class全类名
            className = listenerClass;
        }

        //获取className的全类名的class对象
        Class<?> clazz = Class.forName(className);
        //实例化一个监听器
        LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();

        //给当前对象添加生命周期监听器
        c.addLifecycleListener(listener);
    }


}
