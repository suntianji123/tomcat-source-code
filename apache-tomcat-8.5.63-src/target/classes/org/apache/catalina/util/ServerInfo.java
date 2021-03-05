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


package org.apache.catalina.util;


import java.io.InputStream;
import java.util.Properties;

import org.apache.tomcat.util.ExceptionUtils;


/**
 * 服务器信息类
 *
 */
public class ServerInfo {


    // ------------------------------------------------------- Static Variables


    /**
     * 服务器信息server.info属性值  Apache Tomcat/@VERSION@
     */
    private static final String serverInfo;

    /**
     * 服务器server.built属性值  @VERSION_BUILT@
     */
    private static final String serverBuilt;

    /**
     * 服务器版本号server.number属性值 @VERSION_NUMBER@
     */
    private static final String serverNumber;

    static {
        //信息
        String info = null;
        String built = null;
        //服务器版本号
        String number = null;

        //实例化一个属性对象
        Properties props = new Properties();
        //读取ServerInfo.properties属性文件
        try (InputStream is = ServerInfo.class.getResourceAsStream
                ("/org/apache/catalina/util/ServerInfo.properties")) {
            //将属性文件加载到输入流
            props.load(is);
            //获取server.info属性值  Apache Tomcat/@VERSION@
            info = props.getProperty("server.info");
            //获取server.built属性值 @VERSION_NUMBER@
            built = props.getProperty("server.built");
            //获取server.number属性值
            number = props.getProperty("server.number");
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }
        if (info == null)
            info = "Apache Tomcat 8.5.x-dev";
        if (built == null)
            built = "unknown";
        if (number == null)
            number = "8.5.x";

        //设置serverInfo
        serverInfo = info;
        //设置serverBuilt
        serverBuilt = built;
        //设置serverNumber
        serverNumber = number;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 获取服务器版本号  Apache Tomcat/@VERSION@
     * @return
     */
    public static String getServerInfo() {
        return serverInfo;
    }

    /**
     * @return the server built time for this version of Tomcat.
     */
    public static String getServerBuilt() {
        return serverBuilt;
    }

    /**
     * @return the server's version number.
     */
    public static String getServerNumber() {
        return serverNumber;
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " +
                           System.getProperty("os.name"));
        System.out.println("OS Version:     " +
                           System.getProperty("os.version"));
        System.out.println("Architecture:   " +
                           System.getProperty("os.arch"));
        System.out.println("JVM Version:    " +
                           System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " +
                           System.getProperty("java.vm.vendor"));
    }

}
