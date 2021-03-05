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

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * 版本日志监听器类
 */
public class VersionLoggerListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(VersionLoggerListener.class);

    /**
     * org.apache.catalina.startup包对应的字符串管理器
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 是否记录运行参数
     */
    private boolean logArgs = true;

    /**
     * 是否记录环境参数
     */
    private boolean logEnv = false;

    /**
     * 是否记录属性参数
     */
    private boolean logProps = false;


    public boolean getLogArgs() {
        return logArgs;
    }


    public void setLogArgs(boolean logArgs) {
        this.logArgs = logArgs;
    }


    public boolean getLogEnv() {
        return logEnv;
    }


    public void setLogEnv(boolean logEnv) {
        this.logEnv = logEnv;
    }


    public boolean getLogProps() {
        return logProps;
    }


    public void setLogProps(boolean logProps) {
        this.logProps = logProps;
    }


    /**
     * 处理生命周期事件
     * @param event 生命周期事件对象
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {//初始化之前
            //记录日志
            log();
        }
    }


    /**
     * 从org.apache.catalina.startup.LocalStrings.properties中读取相关属性值
     * 打印日志
     */
    private void log() {
        // Server version name:   {0}
        log.info(sm.getString("versionLoggerListener.serverInfo.server.version", ServerInfo.getServerInfo()));
        //Server built:          {0}
        log.info(sm.getString("versionLoggerListener.serverInfo.server.built",
                ServerInfo.getServerBuilt()));
        //Server version number: {0}
        log.info(sm.getString("versionLoggerListener.serverInfo.server.number",
                ServerInfo.getServerNumber()));
        //OS Name:               {0}
        log.info(sm.getString("versionLoggerListener.os.name",
                System.getProperty("os.name")));
        //OS Version:            {0}
        log.info(sm.getString("versionLoggerListener.os.version",
                System.getProperty("os.version")));
        //Architecture:          {0}
        log.info(sm.getString("versionLoggerListener.os.arch",
                System.getProperty("os.arch")));
        //Java Home:             {0}
        log.info(sm.getString("versionLoggerListener.java.home",
                System.getProperty("java.home")));
        //JVM Version:           {0}
        log.info(sm.getString("versionLoggerListener.vm.version",
                System.getProperty("java.runtime.version")));
        //JVM Vendor:            {0}
        log.info(sm.getString("versionLoggerListener.vm.vendor",
                System.getProperty("java.vm.vendor")));
        //CATALINA_BASE:         {0}
        log.info(sm.getString("versionLoggerListener.catalina.base",
                System.getProperty("catalina.base")));
        //CATALINA_HOME:         {0}
        log.info(sm.getString("versionLoggerListener.catalina.home",
                System.getProperty("catalina.home")));

        if (logArgs) {//如果运行main方法传入的参数
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {//遍历所有的参数 记录
                //Command line argument: {0}
                log.info(sm.getString("versionLoggerListener.arg", arg));
            }
        }

        if (logEnv) {//记录环境参数
            SortedMap<String, String> sortedMap = new TreeMap<>(System.getenv());
            for (Map.Entry<String, String> e : sortedMap.entrySet()) {
                //Environment variable:  {0} = {1}
                log.info(sm.getString("versionLoggerListener.env", e.getKey(), e.getValue()));
            }
        }

        if (logProps) {//记录属性
            SortedMap<String, String> sortedMap = new TreeMap<>();
            for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
                sortedMap.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            for (Map.Entry<String, String> e : sortedMap.entrySet()) {
                //System property:       {0} = {1}
                log.info(sm.getString("versionLoggerListener.prop", e.getKey(), e.getValue()));
            }
        }
    }
}
