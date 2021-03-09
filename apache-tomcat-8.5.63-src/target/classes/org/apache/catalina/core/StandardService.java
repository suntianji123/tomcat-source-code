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


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;
import org.apache.catalina.mapper.MapperListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * 标准的服务类
 */
public class StandardService extends LifecycleMBeanBase implements Service {

    private static final Log log = LogFactory.getLog(StandardService.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * 服务名称
     */
    private String name = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * 服务的服务器对象
     */
    private Server server = null;

    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 连接器列表
     */
    protected Connector connectors[] = new Connector[0];

    /**
     * 访问连接器数组的锁
     */
    private final Object connectorsLock = new Object();

    /**
     * 执行器列表
     */
    protected final ArrayList<Executor> executors = new ArrayList<>();

    private Engine engine = null;

    private ClassLoader parentClassLoader = null;

    /**
     * 文件夹对象
     */
    protected final Mapper mapper = new Mapper();


    /**
     * Mapper listener.
     */
    protected final MapperListener mapperListener = new MapperListener(this);


    // ------------------------------------------------------------- Properties

    /**
     * 获取问价夹对象
     * @return
     */
    @Override
    public Mapper getMapper() {
        return mapper;
    }


    @Override
    public Engine getContainer() {
        return engine;
    }


    /**
     * 设置service的容器对象 比如StandardEngine引擎
     * @param engine 引擎容器对象
     */
    @Override
    public void setContainer(Engine engine) {
        //获取老的引擎容器对象
        Engine oldEngine = this.engine;
        if (oldEngine != null) {//存在老的容器
            //设置老的引擎对象的service为null
            oldEngine.setService(null);
        }

        //设置当前service对象的引擎为新的引擎
        this.engine = engine;
        if (this.engine != null) {//如果引擎不为null
            //设置引擎的service为当前service
            this.engine.setService(this);
        }
        if (getState().isAvailable()) {//如果当前服务可用 执行
            if (this.engine != null) {
                try {
                    this.engine.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.engine.startFailed"), e);
                }
            }
            // Restart MapperListener to pick up new engine.
            try {
                mapperListener.stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardService.mapperListener.stopFailed"), e);
            }
            try {
                mapperListener.start();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardService.mapperListener.startFailed"), e);
            }
            if (oldEngine != null) {
                try {
                    oldEngine.stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.engine.stopFailed"), e);
                }
            }
        }

        // 下发设置服务引擎容器事件
        support.firePropertyChange("container", oldEngine, this.engine);
    }


    /**
     * Return the name of this Service.
     */
    @Override
    public String getName() {
        return name;
    }


    /**
     * 设置服务名称
     * @param name The new service name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }


    /**
     * Return the <code>Server</code> with which we are associated (if any).
     */
    @Override
    public Server getServer() {
        return this.server;
    }


    /**
     * 设置服务的服务器对象
     * @param server 服务的服务器对象
     */
    @Override
    public void setServer(Server server) {
        this.server = server;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加连接器方法
     * @param connector 连接器对象
     */
    @Override
    public void addConnector(Connector connector) {

        synchronized (connectorsLock) {//连接加锁
            //设置连接器的服务对象
            connector.setService(this);
            //扩展数据
            Connector results[] = new Connector[connectors.length + 1];
            //将原始连接器数组复制到新的数组
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            //将新添加的连接器设置到最后一个连接器数组的最后一个元素
            results[connectors.length] = connector;
            //设置连接数组为新的连接器数组
            connectors = results;

            if (getState().isAvailable()) {//获取服务的状态为可用
                try {
                    connector.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }

            //下发service连接器数组改变事件
            support.firePropertyChange("connector", null, connector);
        }

    }


    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for (int i=0; i<results.length; i++) {
            results[i] = connectors[i].getObjectName();
        }
        return results;
    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * Find and return the set of Connectors associated with this Service.
     */
    @Override
    public Connector[] findConnectors() {
        return connectors;
    }


    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    @Override
    public void removeConnector(Connector connector) {

        synchronized (connectorsLock) {
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (connectors[j].getState().isAvailable()) {
                try {
                    connectors[j].stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connectors[j]), e);
                }
            }
            connector.setService(null);
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;

            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }
    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }


    /**
     * Adds a named executor to the service
     * @param ex Executor
     */
    @Override
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (getState().isAvailable()) {
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error("Executor.start", x);
                    }
                }
            }
        }
    }


    /**
     * Retrieves all executors
     * @return Executor[]
     */
    @Override
    public Executor[] findExecutors() {
        synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
    }


    /**
     * 根据执行器名 获取执行器
     * @param executorName 执行器名
     * @return
     */
    @Override
    public Executor getExecutor(String executorName) {
        synchronized (executors) {//加锁执行器数组
            for (Executor executor: executors) {//遍历StanderService中所有的执行器 找出名字与遍历的执行器名相同的执行器
                if (executorName.equals(executor.getName()))
                    return executor;
            }
        }
        return null;
    }


    /**
     * Removes an executor from the service
     * @param ex Executor
     */
    @Override
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if ( executors.remove(ex) && getState().isAvailable() ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error("Executor.stop", e);
                }
            }
        }
    }


    /**
     * 启动StandardService
     * @throws LifecycleException
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.start.name", this.name));

        //设置状态启动中
        setState(LifecycleState.STARTING);

        // Start our defined Container first
        if (engine != null) {//首先启动StandardEngine 引擎
            synchronized (engine) {
                engine.start();
            }
        }

        synchronized (executors) {//启动所有的执行器
            for (Executor executor: executors) {
                executor.start();
            }
        }

        //启动文件匹配器
        mapperListener.start();

        // Start our defined Connectors second
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    // If it has already failed, don't try and start it
                    if (connector.getState() != LifecycleState.FAILED) {
                        connector.start();
                    }
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }
        }
    }


    /**
     * Stop nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Pause connectors first
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    connector.pause();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.pauseFailed",
                            connector), e);
                }
                // Close server socket if bound on start
                // Note: test is in AbstractEndpoint
                connector.getProtocolHandler().closeServerSocketGraceful();
            }
        }

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.stop.name", this.name));
        setState(LifecycleState.STOPPING);

        // Stop our defined Container second
        if (engine != null) {
            synchronized (engine) {
                engine.stop();
            }
        }

        // Now stop the connectors
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (!LifecycleState.STARTED.equals(
                        connector.getState())) {
                    // Connectors only need stopping if they are currently
                    // started. They may have failed to start or may have been
                    // stopped (e.g. via a JMX call)
                    continue;
                }
                try {
                    connector.stop();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connector), e);
                }
            }
        }

        // If the Server failed to start, the mapperListener won't have been
        // started
        if (mapperListener.getState() != LifecycleState.INITIALIZED) {
            mapperListener.stop();
        }

        synchronized (executors) {
            for (Executor executor: executors) {
                executor.stop();
            }
        }
    }


    /**
     * 初始化服务
     * @throws LifecycleException
     */
    @Override
    protected void initInternal() throws LifecycleException {
        //将当前服务对象包装为动态的MBean对象 注册到MBeanServer仓库 索引名 type=service
        super.initInternal();

        if (engine != null) {//服务的引擎 不为null
            //初始化引擎对象
            engine.init();
        }

        // Initialize any Executors
        for (Executor executor : findExecutors()) {//初始化所有的执行器
            if (executor instanceof JmxEnabled) {
                ((JmxEnabled) executor).setDomain(getDomain());
            }
            executor.init();
        }

        // 初始化文件夹监听器对象
        //将MappedListener对象包装为动态的MBean对象 注册到MBeanServer仓库 索引名type=Mapper
        mapperListener.init();

        //初始化连接器
        synchronized (connectorsLock) {//连接锁加锁
            for (Connector connector : connectors) {//遍历所有的连接器对象
                try {
                    //初始化连接器
                    connector.init();
                } catch (Exception e) {
                    String message = sm.getString(
                            "standardService.connector.initFailed", connector);
                    log.error(message, e);

                    if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE"))
                        throw new LifecycleException(message);
                }
            }
        }
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        mapperListener.destroy();

        // Destroy our defined Connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                try {
                    connector.destroy();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.destroyFailed", connector), e);
                }
            }
        }

        // Destroy any Executors
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        if (engine != null) {
            engine.destroy();
        }

        super.destroyInternal();
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (server != null) {
            return server.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }


    /**
     * 获取领域
     * @return
     */
    @Override
    protected String getDomainInternal() {
        //结果领域
        String domain = null;
        //获取引擎对象
        Container engine = getContainer();

        // Use the engine name first
        if (engine != null) {//引擎对象存在
            //获取引擎名
            domain = engine.getName();
        }

        // No engine or no engine name, use the service name
        if (domain == null) {
            domain = getName();
        }

        // No service name, return null which will trigger the use of the
        // default
        return domain;
    }


    @Override
    public final String getObjectNameKeyProperties() {
        return "type=Service";
    }
}
