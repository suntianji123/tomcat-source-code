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

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * 描述符摘要类
 */
public class MbeansDescriptorsDigesterSource extends ModelerSource
{
    private static final Log log =
            LogFactory.getLog(MbeansDescriptorsDigesterSource.class);
    private static final Object dLock = new Object();

    /**
     * 等机器对象
     */
    private Registry registry;
    private final List<ObjectName> mbeans = new ArrayList<>();
    private static Digester digester = null;

    private static Digester createDigester() {

        Digester digester = new Digester();
        digester.setNamespaceAware(false);
        digester.setValidating(false);
        URL url = Registry.getRegistry(null, null).getClass().getResource
            ("/org/apache/tomcat/util/modeler/mbeans-descriptors.dtd");
        digester.register
            ("-//Apache Software Foundation//DTD Model MBeans Configuration File",
                url.toString());

        //解析 org.apache.mbeans.mbeans-descriptors.xml文件
        // 创建一个ManagedBean对象
        //设置默认属性 modelerType
        digester.addObjectCreate
            ("mbeans-descriptors/mbean",
            "org.apache.tomcat.util.modeler.ManagedBean");
        //设置属性值
        digester.addSetProperties
            ("mbeans-descriptors/mbean");
        //将MBean对象添加 loadedMbeans数组
        digester.addSetNext
            ("mbeans-descriptors/mbean",
                "add",
            "java.lang.Object");

        //创建AttributeInfo属性对象
        digester.addObjectCreate
            ("mbeans-descriptors/mbean/attribute",
            "org.apache.tomcat.util.modeler.AttributeInfo");
        //设置属性值
        digester.addSetProperties
            ("mbeans-descriptors/mbean/attribute");
        //将AttributeInfo属性对象作为参数 执行ManagedBean的addAttribute方法
        digester.addSetNext
            ("mbeans-descriptors/mbean/attribute",
                "addAttribute",
            "org.apache.tomcat.util.modeler.AttributeInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/notification",
            "org.apache.tomcat.util.modeler.NotificationInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/notification");
        digester.addSetNext
            ("mbeans-descriptors/mbean/notification",
                "addNotification",
            "org.apache.tomcat.util.modeler.NotificationInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/notification/descriptor/field",
            "org.apache.tomcat.util.modeler.FieldInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/notification/descriptor/field");
        digester.addSetNext
            ("mbeans-descriptors/mbean/notification/descriptor/field",
                "addField",
            "org.apache.tomcat.util.modeler.FieldInfo");

        digester.addCallMethod
            ("mbeans-descriptors/mbean/notification/notification-type",
                "addNotifType", 0);

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation",
            "org.apache.tomcat.util.modeler.OperationInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation",
                "addOperation",
            "org.apache.tomcat.util.modeler.OperationInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation/descriptor/field",
            "org.apache.tomcat.util.modeler.FieldInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation/descriptor/field");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation/descriptor/field",
                "addField",
            "org.apache.tomcat.util.modeler.FieldInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation/parameter",
            "org.apache.tomcat.util.modeler.ParameterInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation/parameter");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation/parameter",
                "addParameter",
            "org.apache.tomcat.util.modeler.ParameterInfo");

        return digester;

    }

    public void setRegistry(Registry reg) {
        this.registry=reg;
    }


    public void setSource( Object source ) {
        this.source=source;
    }

    /**
     * 从url资源中加载描述器
     * @param registry 注册器
     * @param type 资源类型  null
     * @param source url资源对象
     * @return
     * @throws Exception
     */
    @Override
    public List<ObjectName> loadDescriptors( Registry registry, String type,
            Object source) throws Exception {
        //设置登记器对象
        setRegistry(registry);
        //设置url资源对象
        setSource(source);
        //执行加载
        execute();
        return mbeans;
    }

    /**
     * 解析解析 org.apache.mbeans.mbeans-descriptors.xml文件文件 创建ManagedBean对象
     * xml文件中所有配置的ManagedBean对象注册到 MBeanServer仓库
     * @throws Exception
     */
    public void execute() throws Exception {
        if (registry == null) {//等机器不为null
            registry = Registry.getRegistry(null, null);
        }

        //将资源转为输入资源
        InputStream stream = (InputStream) source;

        //所有的MBean列表
        List<ManagedBean> loadedMbeans = new ArrayList<>();
        synchronized(dLock) {//加锁
            if (digester == null) {
                digester = createDigester();
            }

            // Process the input file to configure our registry
            try {
                // 将loadedMbeans列表 放入数据栈
                digester.push(loadedMbeans);
                digester.parse(stream);
            } catch (Exception e) {
                log.error(sm.getString("modules.digesterParseError"), e);
                throw e;
            } finally {
                digester.reset();
            }

        }


        for (ManagedBean loadedMbean : loadedMbeans) {
            registry.addManagedBean(loadedMbean);
        }
    }
}
