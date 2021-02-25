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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Catalina配置属性类
 */
public class CatalinaProperties {

    private static final Log log = LogFactory.getLog(CatalinaProperties.class);

    /**
     * catalina-home/conf/catalina-properties属性文件中设置的属性值对象
     */
    private static Properties properties = null;


    static {
        //加载catalina-home/conf/catalina.properties文件中的属性值到properties对象
        //将properties对象中的属性值设置jvm系统参数
        loadProperties();
    }


    /**
     * 获取catalina-home/conf/catalina.properties中某一个属性的值
     * @param name 属性名
     * @return
     */
    public static String getProperty(String name) {
        //从属性对象中获取属性值
        return properties.getProperty(name);
    }


    /**
     * 静态方法中加载属性
     */
    private static void loadProperties() {

        //定义输入流
        InputStream is = null;
        try {
            //获取jvm参数catalina.config的值 catalina-home/conf/logging.properties
            String configUrl = System.getProperty("catalina.config");
            if (configUrl != null) {//设置配置属性
                //定义一个URL 打开输入流
                is = (new URL(configUrl)).openStream();
            }
        } catch (Throwable t) {
            //线程死亡的异常或者虚拟机运行错误的异常直接抛出
            handleThrowable(t);
        }

        if (is == null) {//输入流为空 指定的配置文件不存在
            try {
                //实例画一个文件 获catalina-home文件
                File home = new File(Bootstrap.getCatalinaBase());
                //获取catalina-home/conf文件
                File conf = new File(home, "conf");
                //获取catalina-home/conf/catalina.properties文件
                File propsFile = new File(conf, "catalina.properties");
                //打开catalina.properties文件的输入流打开
                is = new FileInputStream(propsFile);
            } catch (Throwable t) {
                //处理异常
                handleThrowable(t);
            }
        }

        if (is == null) {//如果catalina-home/conf/catalina.properties属性文件不存在
            try {
                //将当前类作为资源输入流
                is = CatalinaProperties.class.getResourceAsStream
                    ("/org/apache/catalina/startup/catalina.properties");
            } catch (Throwable t) {
                //处理异常
                handleThrowable(t);
            }
        }

        if (is != null) {
            try {
                //实例化属性
                properties = new Properties();
                //将catalina-home/conf/catalina-properties中的属性值加载到properties属性对象
                properties.load(is);
            } catch (Throwable t) {
                //处理异常
                handleThrowable(t);
                //打印警告类型的异常
                log.warn(t);
            } finally {
                try {
                    //关闭子元素输入流
                    is.close();
                } catch (IOException ioe) {
                    //打印关闭catalina.properties文件资源输入流的异常
                    log.warn("Could not close catalina.properties", ioe);
                }
            }
        }

        if ((is == null)) {
            // Do something
            log.warn("Failed to load catalina.properties");
            // That's fine - we have reasonable defaults.
            properties = new Properties();
        }

        //获取catalina.properties属性文件中的所有属性
        Enumeration<?> enumeration = properties.propertyNames();
        while (enumeration.hasMoreElements()) {//遍历属性
            //获取属性名
            String name = (String) enumeration.nextElement();
            //获取属性值
            String value = properties.getProperty(name);
            if (value != null) {//如果属性值不为空
                //将属性设置到异常参数
                System.setProperty(name, value);
            }
        }
    }


    /**
     * 处理异常
     * @param t 异常对象
     */
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {//如果是线程死亡的异常
            //直接将线程死亡的异常抛出
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {//如果是虚拟机运行错误的异常 直接抛出异常
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }
}
