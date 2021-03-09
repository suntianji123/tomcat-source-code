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
package org.apache.catalina.core;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 标准的引擎类
 */
public class StandardEngine extends ContainerBase implements Engine {

    private static final Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    /**
     * 实例化标准的引擎对象
     */
    public StandardEngine() {

        super();
        //设置管道的基本值对象
        pipeline.setBasic(new StandardEngineValve());
        /* Set the jmvRoute using the system property jvmRoute */
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
            log.warn(sm.getString("standardEngine.jvmRouteFail"));
        }
        // By default, the engine will hold the reloading thread
        backgroundProcessorDelay = 10;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 默认的主机地址 默认为localhost
     */
    private String defaultHost = null;


    /**
     * 引擎的服务对象
     */
    private Service service = null;

    /**
     * jvm路径id
     */
    private String jvmRouteId;

    /**
     * Default access log to use for request/response pairs where we can't ID
     * the intended host and context.
     */
    private final AtomicReference<AccessLog> defaultAccessLog =
        new AtomicReference<>();

    // ------------------------------------------------------------- Properties

    /**
     * Obtain the configured Realm and provide a default Realm implementation
     * when no explicit configuration is set.
     *
     * @return configured realm, or a {@link NullRealm} by default
     */
    @Override
    public Realm getRealm() {
        //获取引擎的领域
        Realm configured = super.getRealm();
        // If no set realm has been called - default to NullRealm
        // This can be overridden at engine, context and host level
        if (configured == null) {//引擎没有设置领域
            //实例化 一个空的领域
            configured = new NullRealm();
            //将空的领域设置到引擎容器
            this.setRealm(configured);
        }

        //返回配置的领域
        return configured;
    }


    /**
     * Return the default host.
     */
    @Override
    public String getDefaultHost() {
        return defaultHost;
    }


    /**
     * 设置默认的主机地址
     * @param host 主机地址
     */
    @Override
    public void setDefaultHost(String host) {
        //保留原始的主机地址
        String oldDefaultHost = this.defaultHost;
        if (host == null) {//新的主机地址为null
            //设置默认的主机地址为null
            this.defaultHost = null;
        } else {
            //将地址转为小写
            this.defaultHost = host.toLowerCase(Locale.ENGLISH);
        }
        if (getState().isAvailable()) {//如果引擎的状态为可用
            service.getMapper().setDefaultHostName(host);
        }

        //下发设置默认的主机地址事件
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);

    }


    /**
     * 设置jvm路径
     * @param routeId 路径id
     */
    @Override
    public void setJvmRoute(String routeId) {
        //设置jvm路径id
        jvmRouteId = routeId;
    }


    /**
     * Retrieve the cluster-wide unique identifier for this Engine.
     * This value is only useful in a load-balancing scenario.
     */
    @Override
    public String getJvmRoute() {
        return jvmRouteId;
    }


    /**
     * Return the <code>Service</code> with which we are associated (if any).
     */
    @Override
    public Service getService() {
        return this.service;
    }


    /**
     * Set the <code>Service</code> with which we are associated (if any).
     *
     * @param service The service that owns this Engine
     */
    @Override
    public void setService(Service service) {
        this.service = service;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 添加引擎容器的子容器对象 比如添加StandardHost对象
     * @param child 子容器对象
     *
     */
    @Override
    public void addChild(Container child) {

        if (!(child instanceof Host))//如果child不是Host类型
            throw new IllegalArgumentException
                (sm.getString("standardEngine.notHost"));
        super.addChild(child);

    }


    /**
     * Disallow any attempt to set a parent for this Container, since an
     * Engine is supposed to be at the top of the Container hierarchy.
     *
     * @param container Proposed parent Container
     */
    @Override
    public void setParent(Container container) {

        throw new IllegalArgumentException
            (sm.getString("standardEngine.notParent"));

    }


    /**
     * 初始化引擎对象
     * 如果引擎容器对象没有设置Realm领域 实例化一个空的领域对象 设置给引擎容器
     * 将当前引擎对象包装为动态的MBean对象 注册到MBeanServer仓库 索引名为type=enginer
     * @throws LifecycleException
     */
    @Override
    protected void initInternal() throws LifecycleException {
        // Ensure that a Realm is present before any attempt is made to start
        // one. This will create the default NullRealm if necessary.
        getRealm();

        //将当前引擎对象包装为动态的MBean对象 注册到MBeanServer仓库 索引名type=enginer
        super.initInternal();
    }


    /**
     * 启动方法
     * @throws LifecycleException
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Log our server identification information
        if(log.isInfoEnabled())
            log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());

        // Standard container startup
        super.startInternal();
    }


    /**
     * Override the default implementation. If no access log is defined for the
     * Engine, look for one in the Engine's default host and then the default
     * host's ROOT context. If still none is found, return the default NoOp
     * access log.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
            boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            accessLog.log(request, response, time);
            logged = true;
        }

        if (!logged && useDefault) {
            AccessLog newDefaultAccessLog = defaultAccessLog.get();
            if (newDefaultAccessLog == null) {
                // If we reached this point, this Engine can't have an AccessLog
                // Look in the defaultHost
                Host host = (Host) findChild(getDefaultHost());
                Context context = null;
                if (host != null && host.getState().isAvailable()) {
                    newDefaultAccessLog = host.getAccessLog();

                    if (newDefaultAccessLog != null) {
                        if (defaultAccessLog.compareAndSet(null,
                                newDefaultAccessLog)) {
                            AccessLogListener l = new AccessLogListener(this,
                                    host, null);
                            l.install();
                        }
                    } else {
                        // Try the ROOT context of default host
                        context = (Context) host.findChild("");
                        if (context != null &&
                                context.getState().isAvailable()) {
                            newDefaultAccessLog = context.getAccessLog();
                            if (newDefaultAccessLog != null) {
                                if (defaultAccessLog.compareAndSet(null,
                                        newDefaultAccessLog)) {
                                    AccessLogListener l = new AccessLogListener(
                                            this, null, context);
                                    l.install();
                                }
                            }
                        }
                    }
                }

                if (newDefaultAccessLog == null) {
                    newDefaultAccessLog = new NoopAccessLog();
                    if (defaultAccessLog.compareAndSet(null,
                            newDefaultAccessLog)) {
                        AccessLogListener l = new AccessLogListener(this, host,
                                context);
                        l.install();
                    }
                }
            }

            newDefaultAccessLog.log(request, response, time);
        }
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (service != null) {
            return service.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    /**
     * 获取catalina-base变量所指向的文件夹路径
     * @return
     */
    @Override
    public File getCatalinaBase() {
        if (service != null) {//服务对象 不为null
            //获取服务器对象
            Server s = service.getServer();
            if (s != null) {
                //获取基本的文件夹路径 Catalina-base
                File base = s.getCatalinaBase();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaBase();
    }


    @Override
    public File getCatalinaHome() {
        if (service != null) {
            Server s = service.getServer();
            if (s != null) {
                File base = s.getCatalinaHome();
                if (base != null) {
                    return base;
                }
            }
        }
        // Fall-back
        return super.getCatalinaHome();
    }


    // -------------------- JMX registration  --------------------

    @Override
    protected String getObjectNameKeyProperties() {
        return "type=Engine";
    }


    @Override
    protected String getDomainInternal() {
        return getName();
    }


    // ----------------------------------------------------------- Inner classes
    protected static final class NoopAccessLog implements AccessLog {

        @Override
        public void log(Request request, Response response, long time) {
            // NOOP
        }

        @Override
        public void setRequestAttributesEnabled(
                boolean requestAttributesEnabled) {
            // NOOP

        }

        @Override
        public boolean getRequestAttributesEnabled() {
            // NOOP
            return false;
        }
    }

    protected static final class AccessLogListener
            implements PropertyChangeListener, LifecycleListener,
            ContainerListener {

        private final StandardEngine engine;
        private final Host host;
        private final Context context;
        private volatile boolean disabled = false;

        public AccessLogListener(StandardEngine engine, Host host,
                Context context) {
            this.engine = engine;
            this.host = host;
            this.context = context;
        }

        public void install() {
            engine.addPropertyChangeListener(this);
            if (host != null) {
                host.addContainerListener(this);
                host.addLifecycleListener(this);
            }
            if (context != null) {
                context.addLifecycleListener(this);
            }
        }

        private void uninstall() {
            disabled = true;
            if (context != null) {
                context.removeLifecycleListener(this);
            }
            if (host != null) {
                host.removeLifecycleListener(this);
                host.removeContainerListener(this);
            }
            engine.removePropertyChangeListener(this);
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (disabled) return;

            String type = event.getType();
            if (Lifecycle.AFTER_START_EVENT.equals(type) ||
                    Lifecycle.BEFORE_STOP_EVENT.equals(type) ||
                    Lifecycle.BEFORE_DESTROY_EVENT.equals(type)) {
                // Container is being started/stopped/removed
                // Force re-calculation and disable listener since it won't
                // be re-used
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (disabled) return;
            if ("defaultHost".equals(evt.getPropertyName())) {
                // Force re-calculation and disable listener since it won't
                // be re-used
                engine.defaultAccessLog.set(null);
                uninstall();
            }
        }

        @Override
        public void containerEvent(ContainerEvent event) {
            // Only useful for hosts
            if (disabled) return;
            if (Container.ADD_CHILD_EVENT.equals(event.getType())) {
                Context context = (Context) event.getData();
                if (context.getPath().isEmpty()) {
                    // Force re-calculation and disable listener since it won't
                    // be re-used
                    engine.defaultAccessLog.set(null);
                    uninstall();
                }
            }
        }
    }
}
