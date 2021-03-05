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
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.security.SecurityConfig;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;


/**
 * Startup/Shutdown shell program for Catalina.  The following command line
 * options are recognized:
 * <ul>
 * <li><b>-config {pathname}</b> - Set the pathname of the configuration file
 *     to be processed.  If a relative path is specified, it will be
 *     interpreted as relative to the directory pathname specified by the
 *     "catalina.base" system property.   [conf/server.xml]</li>
 * <li><b>-help</b>      - Display usage information.</li>
 * <li><b>-nonaming</b>  - Disable naming support.</li>
 * <li><b>configtest</b> - Try to test the config</li>
 * <li><b>start</b>      - Start an instance of Catalina.</li>
 * <li><b>stop</b>       - Stop the currently running instance of Catalina.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class Catalina {


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables

    /**
     * Use await.
     */
    protected boolean await = false;

    /**
     * 创建Server的配置文件路径
     */
    protected String configFile = "conf/server.xml";

    /**
     * 父classLoader
     */
    protected ClassLoader parentClassLoader =
        Catalina.class.getClassLoader();


    /**
     * 服务器对象
     */
    protected Server server = null;


    /**
     * Use shutdown hook flag.
     */
    protected boolean useShutdownHook = true;


    /**
     * Shutdown hook.
     */
    protected Thread shutdownHook = null;


    /**
     * 启动名字
     */
    protected boolean useNaming = true;


    /**
     * Prevent duplicate loads.
     */
    protected boolean loaded = false;


    // ----------------------------------------------------------- Constructors

    public Catalina() {
        setSecurityProtection();
        ExceptionUtils.preload();
    }


    // ------------------------------------------------------------- Properties

    public void setConfigFile(String file) {
        configFile = file;
    }


    public String getConfigFile() {
        return configFile;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }


    /**
     * Set the shared extensions class loader.
     *
     * @param parentClassLoader The shared extensions class loader.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * 设置服务器对象
     * @param server 服务器对象
     */
    public void setServer(Server server) {
        this.server = server;
    }


    /**
     * 获取服务器对象
     * @return
     */
    public Server getServer() {
        return server;
    }


    /**
     * @return <code>true</code> if naming is enabled.
     */
    public boolean isUseNaming() {
        return this.useNaming;
    }


    /**
     * Enables or disables naming support.
     *
     * @param useNaming The new use naming value
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }

    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * 判断load方法的String[] 参数值的合法性
     * @param args 参数组 string[]数组
     * @return
     */
    protected boolean arguments(String args[]) {

        //是否已经配置
        boolean isConfig = false;

        if (args.length < 1) {//参数值数组中没有元素
            //打印参数不可用
            usage();
            return false;
        }

        for (String arg : args) {//遍历参数值数组
            if (isConfig) {
                configFile = arg;
                isConfig = false;
            } else if (arg.equals("-config")) {
                isConfig = true;
            } else if (arg.equals("-nonaming")) {
                setUseNaming(false);
            } else if (arg.equals("-help")) {
                usage();
                return false;
            } else if (arg.equals("start")) {//启动
                // NOOP
            } else if (arg.equals("configtest")) {
                // NOOP
            } else if (arg.equals("stop")) {
                // NOOP
            } else {
                usage();
                return false;
            }
        }

        return true;
    }


    /**
     * 创建配置文件  e:\learn\tomcat\catalina-home\conf\server.xml
     * @return
     */
    protected File configFile() {
        //实例化一个文件
        File file = new File(configFile);
        if (!file.isAbsolute()) {//e:\learn\tomcat\catalina-home\conf\server.xml
            file = new File(Bootstrap.getCatalinaBase(), configFile);
        }
        return file;

    }


    /**
     * 创建用于启动的配置
     * @return
     */
    protected Digester createStartDigester() {

        //当前系统时间
        long t1=System.currentTimeMillis();
        // Initialize the digester

        //实例化一个用于Catalina启动的Digester对象
        Digester digester = new Digester();
        //设置不校验
        digester.setValidating(false);
        //设置规则校验为true
        digester.setRulesValidation(true);
        //伪造的属性map
        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();
        //属性列表
        List<String> objectAttrs = new ArrayList<>();
        //添加className属性
        objectAttrs.add("className");
        //将Object的class对象放入 伪造的属性map中
        fakeAttributes.put(Object.class, objectAttrs);
        // 上下文属性
        List<String> contextAttrs = new ArrayList<>();
        //添加source
        contextAttrs.add("source");
        //将StandardContext.class对象放入 伪造的属性map中
        fakeAttributes.put(StandardContext.class, contextAttrs);
        //设置伪造的属性map
        digester.setFakeAttributes(fakeAttributes);
        //设置使用上下文加载器
        digester.setUseContextClassLoader(true);

        // 设置 解析server.xml中的Server标签 创建Server对象
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        //设置Server对象的属性
        digester.addSetProperties("Server");

        //将Server对象设置Catalina对象
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        //创建NamingResourcesImpl对象
        digester.addObjectCreate("Server/GlobalNamingResources",
                                 "org.apache.catalina.deploy.NamingResourcesImpl");

        //设置NamingResourcesImpl对象的属性值
        digester.addSetProperties("Server/GlobalNamingResources");

        //将NamingResourcesImpl添加到Server对象的globalNamingResources属性
        digester.addSetNext("Server/GlobalNamingResources",
                            "setGlobalNamingResources",
                            "org.apache.catalina.deploy.NamingResourcesImpl");

        //设置 解析server.xml中的Server标签下的子标签Listener标签 创建Listener对象 设置属性
        digester.addObjectCreate("Server/Listener",
                                 null, // 必须通过server.xml配置文件制定监听器的全类名
                                 "className");

        //设置通过标签创建的Listener对象的属性
        digester.addSetProperties("Server/Listener");

        //将Listener对象添加到Server的生命周期监听器列表
        digester.addSetNext("Server/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service",
                                 "org.apache.catalina.core.StandardService",
                                 "className");
        digester.addSetProperties("Server/Service");
        digester.addSetNext("Server/Service",
                            "addService",
                            "org.apache.catalina.Service");

        digester.addObjectCreate("Server/Service/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Listener");
        digester.addSetNext("Server/Service/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        //Executor
        digester.addObjectCreate("Server/Service/Executor",
                         "org.apache.catalina.core.StandardThreadExecutor",
                         "className");
        digester.addSetProperties("Server/Service/Executor");

        digester.addSetNext("Server/Service/Executor",
                            "addExecutor",
                            "org.apache.catalina.Executor");


        //创建Connector连接器对象  设置连接器的协议处理器
        //如果标签的属性中 指定了executor属性 设置连接器的协议处理器的执行器
        //如果标签的属性中 指定了sslImplementationName 设置连接器的协议处理器的ssl证书实现器名称
        digester.addRule("Server/Service/Connector",
                         new ConnectorCreateRule());
        //设置Connector对象的属性值
        digester.addRule("Server/Service/Connector",
                         new SetAllPropertiesRule(new String[]{"executor", "sslImplementationName"}));

        //执行父标签对象StandardService的addConnecot方法
        digester.addSetNext("Server/Service/Connector",
                            "addConnector",
                            "org.apache.catalina.connector.Connector");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig",
                                 "org.apache.tomcat.util.net.SSLHostConfig");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig",
                "addSslHostConfig",
                "org.apache.tomcat.util.net.SSLHostConfig");

        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new CertificateCreateRule());
        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate",
                         new SetAllPropertiesRule(new String[]{"type"}));
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/Certificate",
                            "addCertificate",
                            "org.apache.tomcat.util.net.SSLHostConfigCertificate");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConf");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf",
                            "setOpenSslConf",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConf");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                                 "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd",
                            "addCmd",
                            "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");

        digester.addObjectCreate("Server/Service/Connector/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Connector/UpgradeProtocol",
                                  null, // MUST be specified in the element
                                  "className");
        digester.addSetProperties("Server/Service/Connector/UpgradeProtocol");
        digester.addSetNext("Server/Service/Connector/UpgradeProtocol",
                            "addUpgradeProtocol",
                            "org.apache.coyote.UpgradeProtocol");

        //添加Server/GlobalNamingResources/标签下所有子标签的解析规则
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));

        //添加Server/Service/标签下所有的子标签的解析规则
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        addClusterRuleSet(digester, "Server/Service/Engine/Host/Cluster/");
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine",
                         new SetParentClassLoaderRule(parentClassLoader));
        addClusterRuleSet(digester, "Server/Service/Engine/Cluster/");

        long t2=System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("Digester for server.xml created " + ( t2-t1 ));
        }
        return digester;

    }

    /**
     * Cluster support is optional. The JARs may have been removed.
     */
    private void addClusterRuleSet(Digester digester, String prefix) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName("org.apache.catalina.ha.ClusterRuleSet");
            constructor = clazz.getConstructor(String.class);
            RuleSet ruleSet = (RuleSet) constructor.newInstance(prefix);
            digester.addRuleSet(ruleSet);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("catalina.noCluster",
                        e.getClass().getName() + ": " +  e.getMessage()));
            }
        }
    }

    /**
     * Create and configure the Digester we will be using for shutdown.
     * @return the digester to process the stop operation
     */
    protected Digester createStopDigester() {

        // Initialize the digester
        Digester digester = new Digester();
        digester.setUseContextClassLoader(true);

        // Configure the rules we need for shutting down
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        return digester;

    }


    public void stopServer() {
        stopServer(null);
    }

    public void stopServer(String[] arguments) {

        if (arguments != null) {
            arguments(arguments);
        }

        Server s = getServer();
        if (s == null) {
            // Create and execute our Digester
            Digester digester = createStopDigester();
            File file = configFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                InputSource is =
                    new InputSource(file.toURI().toURL().toString());
                is.setByteStream(fis);
                digester.push(this);
                digester.parse(is);
            } catch (Exception e) {
                log.error("Catalina.stop: ", e);
                System.exit(1);
            }
        } else {
            // Server object already present. Must be running as a service
            try {
                s.stop();
                s.destroy();
            } catch (LifecycleException e) {
                log.error("Catalina.stop: ", e);
            }
            return;
        }

        // Stop the existing server
        s = getServer();
        if (s.getPort()>0) {
            try (Socket socket = new Socket(s.getAddress(), s.getPort());
                    OutputStream stream = socket.getOutputStream()) {
                String shutdown = s.getShutdown();
                for (int i = 0; i < shutdown.length(); i++) {
                    stream.write(shutdown.charAt(i));
                }
                stream.flush();
            } catch (ConnectException ce) {
                log.error(sm.getString("catalina.stopServer.connectException",
                                       s.getAddress(),
                                       String.valueOf(s.getPort())));
                log.error("Catalina.stop: ", ce);
                System.exit(1);
            } catch (IOException e) {
                log.error("Catalina.stop: ", e);
                System.exit(1);
            }
        } else {
            log.error(sm.getString("catalina.stopServer"));
            System.exit(1);
        }
    }


    /**
     * 创建StandardServer LifecycleListener StandardService StandardEnginer Connector对象
     * 初始化这些对象 将对象包装为动态的MBean 将他们注册到MBeanServer仓库
     */
    public void load() {

        if (loaded) {//如果已经加载过 直接返回
            return;
        }

        //设置已经贾在国
        loaded = true;

        //获取系统时间
        long t1 = System.nanoTime();

        //初始化文件夹
        initDirs();

        // Before digester - it may be needed
        //初始化名字

        //设置jvm参数catalina.useNaming=true
        //设置jvm参数java.naming.factory.url.pkgs=org.apache.naming
        //设置jvm参数java.naming.factory.initial=org.apache.naming.java.javaURLContextFactory
        initNaming();

        // 创建digester对象
        Digester digester = createStartDigester();

        //资源输入对象
        InputSource inputSource = null;
        //资源输入流对象
        InputStream inputStream = null;
        //文件
        File file = null;
        try {
            try {

                //e:\learn\tomcat\catalina-home\conf\server.xml
                file = configFile();
                //server.xml文件输入流
                inputStream = new FileInputStream(file);
                //server.xml文件资源
                inputSource = new InputSource(file.toURI().toURL().toString());
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("catalina.configFail", file), e);
                }
            }

            //文件输入流
            if (inputStream == null) {
                try {
                    inputStream = getClass().getClassLoader()
                        .getResourceAsStream(getConfigFile());
                    inputSource = new InputSource
                        (getClass().getClassLoader()
                         .getResource(getConfigFile()).toString());
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("catalina.configFail",
                                getConfigFile()), e);
                    }
                }
            }

            // This should be included in catalina.jar
            // Alternative: don't bother with xml, just create it manually.
            if (inputStream == null) {
                try {
                    inputStream = getClass().getClassLoader()
                            .getResourceAsStream("server-embed.xml");
                    inputSource = new InputSource
                    (getClass().getClassLoader()
                            .getResource("server-embed.xml").toString());
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("catalina.configFail",
                                "server-embed.xml"), e);
                    }
                }
            }


            if (inputStream == null || inputSource == null) {
                if  (file == null) {
                    log.warn(sm.getString("catalina.configFail",
                            getConfigFile() + "] or [server-embed.xml]"));
                } else {
                    log.warn(sm.getString("catalina.configFail",
                            file.getAbsolutePath()));
                    if (file.exists() && !file.canRead()) {
                        log.warn("Permissions incorrect, read permission is not allowed on the file.");
                    }
                }
                return;
            }

            try {

                //设置字节流
                inputSource.setByteStream(inputStream);

                //将当前对象放入digester的数组栈
                digester.push(this);
                //解析Server.xml资源
                digester.parse(inputSource);
            } catch (SAXParseException spe) {
                log.warn("Catalina.start using " + getConfigFile() + ": " +
                        spe.getMessage());
                return;
            } catch (Exception e) {
                log.warn("Catalina.start using " + getConfigFile() + ": " , e);
                return;
            }
        } finally {
            if (inputStream != null) {
                try {
                    //关闭输入流
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        //设置StandardServer对象的Catalina对象
        getServer().setCatalina(this);
        //设置StandardServer对象的Catalina-home路径文件夹对象
        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
        //设置StandardServer对象的Catalina-base路径文件件对象
        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        //初始化流
        initStreams();

        // Start the new server
        try {
            //初始化服务器
            getServer().init();
        } catch (LifecycleException e) {
            if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
                throw new java.lang.Error(e);
            } else {
                log.error("Catalina.start", e);
            }
        }

        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info("Initialization processed in " + ((t2 - t1) / 1000000) + " ms");
        }
    }


    /**
     * load方法
     * @param args 参数为 ["start"]
     */
    public void load(String args[]) {

        try {
            if (arguments(args)) {//参数合法
                //调用加载方法
                load();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    /**
     * 启动方法
     */
    public void start() {

        if (getServer() == null) {//init没有完成 加载服务器
            load();
        }

        if (getServer() == null) {
            log.fatal("Cannot start server. Server instance is not configured.");
            return;
        }

        long t1 = System.nanoTime();

        // Start the new server
        try {
            //启动服务器
            getServer().start();
        } catch (LifecycleException e) {
            log.fatal(sm.getString("catalina.serverStartFail"), e);
            try {
                getServer().destroy();
            } catch (LifecycleException e1) {
                log.debug("destroy() failed for failed Server ", e1);
            }
            return;
        }

        long t2 = System.nanoTime();
        if(log.isInfoEnabled()) {
            log.info("Server startup in " + ((t2 - t1) / 1000000) + " ms");
        }

        // Register shutdown hook
        if (useShutdownHook) {
            if (shutdownHook == null) {
                shutdownHook = new CatalinaShutdownHook();
            }
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // If JULI is being used, disable JULI's shutdown hook since
            // shutdown hooks run in parallel and log messages may be lost
            // if JULI's hook completes before the CatalinaShutdownHook()
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                        false);
            }
        }

        if (await) {
            await();
            stop();
        }
    }


    /**
     * Stop an existing server instance.
     */
    public void stop() {

        try {
            // Remove the ShutdownHook first so that server.stop()
            // doesn't get invoked twice
            if (useShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);

                // If JULI is being used, re-enable JULI's shutdown to ensure
                // log messages are not lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).setUseShutdownHook(
                            true);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }

        // Shut down the server
        try {
            Server s = getServer();
            LifecycleState state = s.getState();
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                    && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                // Nothing to do. stop() was already called
            } else {
                s.stop();
                s.destroy();
            }
        } catch (LifecycleException e) {
            log.error("Catalina.stop", e);
        }

    }


    /**
     * Await and shutdown.
     */
    public void await() {

        getServer().await();

    }


    /**
     * 打印参数不可用
     */
    protected void usage() {

        System.out.println
            ("usage: java org.apache.catalina.startup.Catalina"
             + " [ -config {pathname} ]"
             + " [ -nonaming ] "
             + " { -help | start | stop }");

    }


    /**
     * @deprecated unused. Will be removed in Tomcat 10 onwards.
     */
    @Deprecated
    protected void initDirs() {
    }


    /**
     * 初始化流
     */
    protected void initStreams() {
       //设置System.out处理
        System.setOut(new SystemLogHandler(System.out));
        //设置System.err处理
        System.setErr(new SystemLogHandler(System.err));
    }


    /**
     * 设置jvm参数catalina.useNaming=true
     * 设置jvm参数java.naming.factory.url.pkgs=org.apache.naming
     * 设置jvm参数java.naming.factory.initial=org.apache.naming.java.javaURLContextFactory
     * 初始化名字
     */
    protected void initNaming() {
        // Setting additional variables
        if (!useNaming) {//不启用名字
            log.info(sm.getString("catalina.noNaming"));
            System.setProperty("catalina.useNaming", "false");
        } else {//启用名字
            //设置jvm系统参数catalina.useNaming= true
            System.setProperty("catalina.useNaming", "true");

            String value = "org.apache.naming";

            //获取jvm参数 java.naming.factory.url.pkgs的值
            String oldValue =
                System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {//存在值
                //新的值为 org
                value = value + ":" + oldValue;
            }

            //设置 jvm参数java.naming.factory.url.pkgs的值为org.apache.naming
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if( log.isDebugEnabled() ) {//记录设置名字的前缀
                log.debug("Setting naming prefix=" + value);
            }

            //获取jvm参数java.naming.factory.initial的值
            value = System.getProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {//值不存在

                //设置jvm参数 java.naming.factory.initial的值
                System.setProperty
                    (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                     "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug( "INITIAL_CONTEXT_FACTORY already set " + value );
            }
        }
    }


    /**
     * Set the security package access/protection.
     */
    protected void setSecurityProtection(){
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }


    // --------------------------------------- CatalinaShutdownHook Inner Class

    // XXX Should be moved to embedded !
    /**
     * Shutdown hook which will perform a clean shutdown of Catalina if needed.
     */
    protected class CatalinaShutdownHook extends Thread {

        @Override
        public void run() {
            try {
                if (getServer() != null) {
                    Catalina.this.stop();
                }
            } catch (Throwable ex) {
                ExceptionUtils.handleThrowable(ex);
                log.error(sm.getString("catalina.shutdownHookFail"), ex);
            } finally {
                // If JULI is used, shut JULI down *after* the server shuts down
                // so log messages aren't lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).shutdown();
                }
            }
        }
    }


    private static final Log log = LogFactory.getLog(Catalina.class);

}


// ------------------------------------------------------------ Private Classes


/**
 * Rule that sets the parent class loader for the top object on the stack,
 * which must be a <code>Container</code>.
 */

final class SetParentClassLoaderRule extends Rule {

    public SetParentClassLoaderRule(ClassLoader parentClassLoader) {

        this.parentClassLoader = parentClassLoader;

    }

    ClassLoader parentClassLoader = null;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug("Setting parent class loader");
        }

        Container top = (Container) digester.peek();
        top.setParentClassLoader(parentClassLoader);

    }


}
