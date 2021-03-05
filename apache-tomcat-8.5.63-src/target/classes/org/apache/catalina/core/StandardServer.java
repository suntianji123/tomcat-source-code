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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.util.Random;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.mbeans.MBeanFactory;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.res.StringManager;


/**
 * 标准的服务器类
 */
public final class StandardServer extends LifecycleMBeanBase implements Server {

    private static final Log log = LogFactory.getLog(StandardServer.class);


    // ------------------------------------------------------------ Constructor


    /**
     * 实例化一个标准的服务器类
     */
    public StandardServer() {

        super();

        //全局的命名资源
        globalNamingResources = new NamingResourcesImpl();

        //设置全局的命名资源的容器
        globalNamingResources.setContainer(this);

        if (isUseNaming()) {//使用名字的前缀
            namingContextListener = new NamingContextListener();
            addLifecycleListener(namingContextListener);
        } else {
            namingContextListener = null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 全局的名字上下文  UserDatabase ->ResouceRef
     */
    private javax.naming.Context globalNamingContext = null;


    /**
     * 全局的命名资源
     */
    private NamingResourcesImpl globalNamingResources = null;


    /**
     * 命名上下文监听器
     */
    private final NamingContextListener namingContextListener;


    /**
     * 服务器端口号
     */
    private int port = 8005;

    /**
     * 服务器地址
     */
    private String address = "localhost";


    /**
     * A random number generator that is <strong>only</strong> used if
     * the shutdown command string is longer than 1024 characters.
     */
    private Random random = null;


    /**
     * 服务器的服务列表
     */
    private Service services[] = new Service[0];

    /**
     * 服务锁
     */
    private final Object servicesLock = new Object();


    /**
     * shutdown名字
     */
    private String shutdown = "SHUTDOWN";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The property change support for this component.
     */
    final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private volatile boolean stopAwait = false;

    private Catalina catalina = null;

    private ClassLoader parentClassLoader = null;

    /**
     * Thread that currently is inside our await() method.
     */
    private volatile Thread awaitThread = null;

    /**
     * Server socket that is used to wait for the shutdown command.
     */
    private volatile ServerSocket awaitSocket = null;

    private File catalinaHome = null;

    private File catalinaBase = null;

    private final Object namingToken = new Object();


    // ------------------------------------------------------------- Properties

    @Override
    public Object getNamingToken() {
        return namingToken;
    }


    /**
     * Return the global naming resources context.
     */
    @Override
    public javax.naming.Context getGlobalNamingContext() {
        return this.globalNamingContext;
    }


    /**
     * Set the global naming resources context.
     *
     * @param globalNamingContext The new global naming resource context
     */
    public void setGlobalNamingContext(javax.naming.Context globalNamingContext) {
        this.globalNamingContext = globalNamingContext;
    }


    /**
     * Return the global naming resources.
     */
    @Override
    public NamingResourcesImpl getGlobalNamingResources() {
        return this.globalNamingResources;
    }


    /**
     * 设置全局的命名资源
     * @param globalNamingResources 命名资源
     */
    @Override
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources) {

        //记录原始命名资源
        NamingResourcesImpl oldGlobalNamingResources =
            this.globalNamingResources;
        //设置全局的名字资源
        this.globalNamingResources = globalNamingResources;
        //设置命名资源的容器
        this.globalNamingResources.setContainer(this);
        //下发全局命名资源属性改变事件
        support.firePropertyChange("globalNamingResources",
                                   oldGlobalNamingResources,
                                   this.globalNamingResources);

    }


    /**
     * Report the current Tomcat Server Release number
     * @return Tomcat release identifier
     */
    public String getServerInfo() {
        return ServerInfo.getServerInfo();
    }


    /**
     * Return the current server built timestamp
     * @return server built timestamp.
     */
    public String getServerBuilt() {
        return ServerInfo.getServerBuilt();
    }


    /**
     * Return the current server's version number.
     * @return server's version number.
     */
    public String getServerNumber() {
        return ServerInfo.getServerNumber();
    }


    /**
     * Return the port number we listen to for shutdown commands.
     */
    @Override
    public int getPort() {
        return this.port;
    }


    /**
     * Set the port number we listen to for shutdown commands.
     *
     * @param port The new port number
     */
    @Override
    public void setPort(int port) {
        this.port = port;
    }


    /**
     * Return the address on which we listen to for shutdown commands.
     */
    @Override
    public String getAddress() {
        return this.address;
    }


    /**
     * Set the address on which we listen to for shutdown commands.
     *
     * @param address The new address
     */
    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Return the shutdown command string we are waiting for.
     */
    @Override
    public String getShutdown() {
        return this.shutdown;
    }


    /**
     * Set the shutdown command we are waiting for.
     *
     * @param shutdown The new shutdown command
     */
    @Override
    public void setShutdown(String shutdown) {
        this.shutdown = shutdown;
    }


    /**
     * Return the outer Catalina startup/shutdown component if present.
     */
    @Override
    public Catalina getCatalina() {
        return catalina;
    }


    /**
     * Set the outer Catalina startup/shutdown component if present.
     */
    @Override
    public void setCatalina(Catalina catalina) {
        this.catalina = catalina;
    }

    // --------------------------------------------------------- Server Methods


    /**
     * 添加服务
     * @param service 将要被添加的服务对象
     */
    @Override
    public void addService(Service service) {

        //设置服务对象的服务器对象
        service.setServer(this);

        synchronized (servicesLock) {//打开服务锁
            //实例化一个新的服务数组
            Service results[] = new Service[services.length + 1];
            //复制原始服务数组到新的服务数组
            System.arraycopy(services, 0, results, 0, services.length);
            //设置数组的最后一个元素为添加的服务器
            results[services.length] = service;
            //将服务数组指向新的服务数组
            services = results;

            if (getState().isAvailable()) {//服务器已经启动
                try {
                    service.start();
                } catch (LifecycleException e) {
                    // Ignore
                }
            }

            //下发服务器的服务变更事件
            support.firePropertyChange("service", null, service);
        }

    }

    public void stopAwait() {
        stopAwait=true;
        Thread t = awaitThread;
        if (t != null) {
            ServerSocket s = awaitSocket;
            if (s != null) {
                awaitSocket = null;
                try {
                    s.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
            t.interrupt();
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    /**
     * Wait until a proper shutdown command is received, then return.
     * This keeps the main thread alive - the thread pool listening for http
     * connections is daemon threads.
     */
    @Override
    public void await() {
        // Negative values - don't wait on port - tomcat is embedded or we just don't like ports
        if (port == -2) {
            // undocumented yet - for embedding apps that are around, alive.
            return;
        }
        if (port==-1) {
            try {
                awaitThread = Thread.currentThread();
                while(!stopAwait) {
                    try {
                        Thread.sleep( 10000 );
                    } catch( InterruptedException ex ) {
                        // continue and check the flag
                    }
                }
            } finally {
                awaitThread = null;
            }
            return;
        }

        // Set up a server socket to wait on
        try {
            awaitSocket = new ServerSocket(port, 1,
                    InetAddress.getByName(address));
        } catch (IOException e) {
            log.error("StandardServer.await: create[" + address
                               + ":" + port
                               + "]: ", e);
            return;
        }

        try {
            awaitThread = Thread.currentThread();

            // Loop waiting for a connection and a valid command
            while (!stopAwait) {
                ServerSocket serverSocket = awaitSocket;
                if (serverSocket == null) {
                    break;
                }

                // Wait for the next connection
                Socket socket = null;
                StringBuilder command = new StringBuilder();
                try {
                    InputStream stream;
                    long acceptStartTime = System.currentTimeMillis();
                    try {
                        socket = serverSocket.accept();
                        socket.setSoTimeout(10 * 1000);  // Ten seconds
                        stream = socket.getInputStream();
                    } catch (SocketTimeoutException ste) {
                        // This should never happen but bug 56684 suggests that
                        // it does.
                        log.warn(sm.getString("standardServer.accept.timeout",
                                Long.valueOf(System.currentTimeMillis() - acceptStartTime)), ste);
                        continue;
                    } catch (AccessControlException ace) {
                        log.warn(sm.getString("standardServer.accept.security"), ace);
                        continue;
                    } catch (IOException e) {
                        if (stopAwait) {
                            // Wait was aborted with socket.close()
                            break;
                        }
                        log.error(sm.getString("standardServer.accept.error"), e);
                        break;
                    }

                    // Read a set of characters from the socket
                    int expected = 1024; // Cut off to avoid DoS attack
                    while (expected < shutdown.length()) {
                        if (random == null)
                            random = new Random();
                        expected += (random.nextInt() % 1024);
                    }
                    while (expected > 0) {
                        int ch = -1;
                        try {
                            ch = stream.read();
                        } catch (IOException e) {
                            log.warn(sm.getString("standardServer.accept.readError"), e);
                            ch = -1;
                        }
                        // Control character or EOF (-1) terminates loop
                        if (ch < 32 || ch == 127) {
                            break;
                        }
                        command.append((char) ch);
                        expected--;
                    }
                } finally {
                    // Close the socket now that we are done with it
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Match against our command string
                boolean match = command.toString().equals(shutdown);
                if (match) {
                    log.info(sm.getString("standardServer.shutdownViaPort"));
                    break;
                } else
                    log.warn(sm.getString("standardServer.invalidShutdownCommand", command.toString()));
            }
        } finally {
            ServerSocket serverSocket = awaitSocket;
            awaitThread = null;
            awaitSocket = null;

            // Close the server socket and return
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }


    /**
     * @return the specified Service (if it exists); otherwise return
     * <code>null</code>.
     *
     * @param name Name of the Service to be returned
     */
    @Override
    public Service findService(String name) {
        if (name == null) {
            return null;
        }
        synchronized (servicesLock) {
            for (Service service : services) {
                if (name.equals(service.getName())) {
                    return service;
                }
            }
        }
        return null;
    }


    /**
     * 当前服务器下的服务列表
     * @return
     */
    @Override
    public Service[] findServices() {
        return services;
    }

    /**
     * @return the JMX service names.
     */
    public ObjectName[] getServiceNames() {
        ObjectName onames[]=new ObjectName[ services.length ];
        for( int i=0; i<services.length; i++ ) {
            onames[i]=((StandardService)services[i]).getObjectName();
        }
        return onames;
    }


    /**
     * Remove the specified Service from the set associated from this
     * Server.
     *
     * @param service The Service to be removed
     */
    @Override
    public void removeService(Service service) {

        synchronized (servicesLock) {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            try {
                services[j].stop();
            } catch (LifecycleException e) {
                // Ignore
            }
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j)
                    results[k++] = services[i];
            }
            services = results;

            // Report this property change to interested listeners
            support.firePropertyChange("service", service, null);
        }

    }


    @Override
    public File getCatalinaBase() {
        if (catalinaBase != null) {
            return catalinaBase;
        }

        catalinaBase = getCatalinaHome();
        return catalinaBase;
    }


    @Override
    public void setCatalinaBase(File catalinaBase) {
        this.catalinaBase = catalinaBase;
    }


    @Override
    public File getCatalinaHome() {
        return catalinaHome;
    }


    @Override
    public void setCatalinaHome(File catalinaHome) {
        this.catalinaHome = catalinaHome;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

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
        StringBuilder sb = new StringBuilder("StandardServer[");
        sb.append(getPort());
        sb.append(']');
        return sb.toString();
    }


    /**
     * Write the configuration information for this entire <code>Server</code>
     * out to the server.xml configuration file.
     *
     * @exception InstanceNotFoundException
     *            if the managed resource object cannot be found
     * @exception MBeanException
     *            if the initializer of the object throws an exception, or
     *            persistence is not supported
     * @exception javax.management.RuntimeOperationsException
     *            if an exception is reported by the persistence mechanism
     */
    public synchronized void storeConfig() throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "storeConfig", null, null);
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardServer.storeConfig.error"), t);
        }
    }


    /**
     * Write the configuration information for <code>Context</code>
     * out to the specified configuration file.
     *
     * @param context the context which should save its configuration
     * @exception InstanceNotFoundException
     *            if the managed resource object cannot be found
     * @exception MBeanException
     *            if the initializer of the object throws an exception
     *            or persistence is not supported
     * @exception javax.management.RuntimeOperationsException
     *            if an exception is reported by the persistence mechanism
     */
    public synchronized void storeContext(Context context) throws InstanceNotFoundException, MBeanException {
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            ObjectName sname = new ObjectName("Catalina:type=StoreConfig");
            if (mserver.isRegistered(sname)) {
                mserver.invoke(sname, "store",
                    new Object[] {context},
                    new String [] { "java.lang.String"});
            } else {
                log.error(sm.getString("standardServer.storeConfig.notAvailable", sname));
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardServer.storeConfig.contextError", context.getName()), t);
        }
    }


    /**
     * 是否使用名字前缀
     * @return
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // Reading the "catalina.useNaming" environment variable
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }
        return useNaming;
    }


    /**
     * 启动本地周期周期对象
     * @throws LifecycleException
     */
    @Override
    protected void startInternal() throws LifecycleException {

        //下发根据配置启动事件
        //实例化一个命名上下文 namingContext对象
        //设置访问/ 目录的令牌对象为namingResourceImpl的令牌对象
        //向NamingContext绑定UserDatabase 对应的资源引用对象 ResourceRef
        //设置返回StandardServer的令牌对象为namingResourceImpl的令牌对象
        //设置StandardServer的上下文对象为NamingContext对象
        //设置StandardServer类加载器classLoader的上下文对象为NamingContext对象
        //设置StandardServer类加载器classLoader加载对象为StandardServer
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);

        //设置服务器的状态启动中
        setState(LifecycleState.STARTING);

        //启动全局的资源对象
        globalNamingResources.start();

        // Start our defined Services
        synchronized (servicesLock) {//启动所有的StandardService
            for (Service service : services) {
                service.start();
            }
        }
    }


    /**
     * Stop nested components ({@link Service}s) and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);

        // Stop our defined Services
        for (Service service : services) {
            service.stop();
        }

        globalNamingResources.stop();

        stopAwait();
    }

    /**
     * 初始化本地
     * @throws LifecycleException
     */
    @Override
    protected void initInternal() throws LifecycleException {

        //初始化本地   将StandardServer包装为动态的MBean对象 注册到MBeanServer仓库  返回MBean对象在仓库中的索引名
        super.initInternal();

        //实例化一个字符串缓存对象   将字符串缓存对象包装为动态的MBean对象 注册到MBeanServer仓库 返回MBean对象在仓库中的索引名
        onameStringCache = register(new StringCache(), "type=StringCache");

        //实例化一个生产MBean的工厂对象
        MBeanFactory factory = new MBeanFactory();
        //设置工厂的容器为StandardServer
        factory.setContainer(this);
        onameMBeanFactory = register(factory, "type=MBeanFactory");

        // 全局命名资源初始化
        globalNamingResources.init();

        // Populate the extension validator with JARs from common and shared
        // class loaders
        if (getCatalina() != null) {
            //获取Catalina对象的URLClassLoader对象
            ClassLoader cl = getCatalina().getParentClassLoader();
            // Walk the class loader hierarchy. Stop at the system class loader.
            // This will add the shared (if present) and common class loaders
            while (cl != null && cl != ClassLoader.getSystemClassLoader()) {
                if (cl instanceof URLClassLoader) {
                    URL[] urls = ((URLClassLoader) cl).getURLs();
                    for (URL url : urls) {//遍历所有的文件
                        if (url.getProtocol().equals("file")) {
                            try {
                                File f = new File (url.toURI());
                                if (f.isFile() &&
                                        f.getName().endsWith(".jar")) {
                                    ExtensionValidator.addSystemResource(f);
                                }
                            } catch (URISyntaxException | IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
                cl = cl.getParent();
            }
        }
        // 初始化所有的服务
        for (Service service : services) {
            //初始化服务
            service.init();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // Destroy our defined Services
        for (Service service : services) {
            service.destroy();
        }

        globalNamingResources.destroy();

        unregister(onameMBeanFactory);

        unregister(onameStringCache);

        super.destroyInternal();
    }

    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return parentClassLoader;
        if (catalina != null) {
            return catalina.getParentClassLoader();
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
     * StringCache对象存储到MBeanServer仓库后  StringCache对象在MBeanServer仓库中的索引名
     * 值为： type=StringCache
     */
    private ObjectName onameStringCache;

    /**
     * MBeanFactory对象存储到MBeanServer仓库后 MBeanFactory对象在MBeanServer仓库中的索引名
     * 值为：type=MBeanFactory
     */
    private ObjectName onameMBeanFactory;

    /**
     * 获取本地领域
     * @return
     */
    @Override
    protected String getDomainInternal() {

        //领域
        String domain = null;

        //查找服务列表
        Service[] services = findServices();
        if (services.length > 0) {//服务的长度大于00
            //获取第0个服务
            Service service = services[0];
            if (service != null) {//存在第0-个服务
                //获取服务的领域 Server/Service/Engine name属性值
                domain = service.getDomain();
            }
        }

        //返回领域
        return domain;
    }


    /**
     * 获取名字属性值
     * @return
     */
    @Override
    protected final String getObjectNameKeyProperties() {
        return "type=Server";
    }
}
