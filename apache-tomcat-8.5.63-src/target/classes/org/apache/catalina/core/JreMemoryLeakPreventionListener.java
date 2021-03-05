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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.sql.DriverManager;
import java.util.StringTokenizer;
import java.util.concurrent.ForkJoinPool;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.SafeForkJoinWorkerThreadFactory;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.res.StringManager;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;

/**
 * 预防jre内存泄漏监听器
 */
public class JreMemoryLeakPreventionListener implements LifecycleListener {

    private static final Log log =
        LogFactory.getLog(JreMemoryLeakPreventionListener.class);
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final String FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.threadFactory";

    /**
     * 是否开始app上下文保护器
     */
    private boolean appContextProtection = false;
    public boolean isAppContextProtection() { return appContextProtection; }
    public void setAppContextProtection(boolean appContextProtection) {
        this.appContextProtection = appContextProtection;
    }

    /**
     * Protect against the memory leak caused when the first call to
     * <code>java.awt.Toolkit.getDefaultToolkit()</code> is triggered
     * by a web application. Defaults to <code>false</code> because a new
     * Thread is launched.
     */
    private boolean awtThreadProtection = false;
    public boolean isAWTThreadProtection() { return awtThreadProtection; }
    public void setAWTThreadProtection(boolean awtThreadProtection) {
      this.awtThreadProtection = awtThreadProtection;
    }

    /**
     * 是否开启gc守护线程保护
     */
    private boolean gcDaemonProtection = true;
    public boolean isGcDaemonProtection() { return gcDaemonProtection; }
    public void setGcDaemonProtection(boolean gcDaemonProtection) {
        this.gcDaemonProtection = gcDaemonProtection;
    }

    /**
     * 是否开启安全策略保护
     */
     private boolean securityPolicyProtection = true;
     public boolean isSecurityPolicyProtection() {
         return securityPolicyProtection;
     }
     public void setSecurityPolicyProtection(boolean securityPolicyProtection) {
         this.securityPolicyProtection = securityPolicyProtection;
     }

    /**
     * Protects against the memory leak caused when the first call to
     * <code>javax.security.auth.login.Configuration</code> is triggered by a
     * web application. This first call populate a static variable with a
     * reference to the context class loader. Defaults to <code>true</code>.
     */
    private boolean securityLoginConfigurationProtection = true;
    public boolean isSecurityLoginConfigurationProtection() {
        return securityLoginConfigurationProtection;
    }
    public void setSecurityLoginConfigurationProtection(
            boolean securityLoginConfigurationProtection) {
        this.securityLoginConfigurationProtection = securityLoginConfigurationProtection;
    }

    /**
     * 是否开启令牌轮训器保护
     */
    private boolean tokenPollerProtection = true;
    public boolean isTokenPollerProtection() { return tokenPollerProtection; }
    public void setTokenPollerProtection(boolean tokenPollerProtection) {
        this.tokenPollerProtection = tokenPollerProtection;
    }

    /**
     * Protect against resources being read for JAR files and, as a side-effect,
     * the JAR file becoming locked. Note this disables caching for all
     * {@link URLConnection}s, regardless of type. Defaults to
     * <code>true</code>.
     */
    private boolean urlCacheProtection = true;
    public boolean isUrlCacheProtection() { return urlCacheProtection; }
    public void setUrlCacheProtection(boolean urlCacheProtection) {
        this.urlCacheProtection = urlCacheProtection;
    }

    /**
     * 是否开启xml解析保护
     */
    private boolean xmlParsingProtection = true;
    public boolean isXmlParsingProtection() { return xmlParsingProtection; }
    public void setXmlParsingProtection(boolean xmlParsingProtection) {
        this.xmlParsingProtection = xmlParsingProtection;
    }

    /**
     * 是否开启lap池保护
     */
    private boolean ldapPoolProtection = true;
    public boolean isLdapPoolProtection() { return ldapPoolProtection; }
    public void setLdapPoolProtection(boolean ldapPoolProtection) {
        this.ldapPoolProtection = ldapPoolProtection;
    }

    /**
     * 是否开启驱动管理保护
     */
    private boolean driverManagerProtection = true;
    public boolean isDriverManagerProtection() {
        return driverManagerProtection;
    }
    public void setDriverManagerProtection(boolean driverManagerProtection) {
        this.driverManagerProtection = driverManagerProtection;
    }

    /**
     * {@link ForkJoinPool} creates a thread pool that, by default,
     * creates threads that retain references to the thread context class
     * loader.
     */
    private boolean forkJoinCommonPoolProtection = true;
    public boolean getForkJoinCommonPoolProtection() {
        return forkJoinCommonPoolProtection;
    }
    public void setForkJoinCommonPoolProtection(boolean forkJoinCommonPoolProtection) {
        this.forkJoinCommonPoolProtection = forkJoinCommonPoolProtection;
    }

    /**
     * List of comma-separated fully qualified class names to load and initialize during
     * the startup of this Listener. This allows to pre-load classes that are known to
     * provoke classloader leaks if they are loaded during a request processing.
     */
    private String classesToInitialize = null;
    public String getClassesToInitialize() {
        return classesToInitialize;
    }
    public void setClassesToInitialize(String classesToInitialize) {
        this.classesToInitialize = classesToInitialize;
    }

    /**
     * 处理生命周期事件
     * @param event 生命周期事件对象
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Initialise these classes when Tomcat starts
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {//初始化事件

            /*
             * First call to this loads all drivers visible to the current class
             * loader and its parents.
             *
             * Note: This is called before the context class loader is changed
             *       because we want any drivers located in CATALINA_HOME/lib
             *       and/or CATALINA_HOME/lib to be visible to DriverManager.
             *       Users wishing to avoid having JDBC drivers loaded by this
             *       class loader should add the JDBC driver(s) to the class
             *       path so they are loaded by the system class loader.
             */
            if (driverManagerProtection) {//如果开启驱动管理保护
                //获取所有的驱动
                DriverManager.getDrivers();
            }

            //获取当前线程的class类加载器
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try
            {
                //设置当前线程的类加载器为系统加载器
                Thread.currentThread().setContextClassLoader(
                        ClassLoader.getSystemClassLoader());

                /*
                 * Several components end up calling:
                 * sun.awt.AppContext.getAppContext()
                 *
                 * Those libraries / components known to trigger memory leaks
                 * due to eventual calls to getAppContext() are:
                 * - Google Web Toolkit via its use of javax.imageio
                 * - Tomcat via its use of java.beans.Introspector.flushCaches()
                 *   in 1.7.0 to 1.7.0_01. From 1.7.0_02 onwards use of
                 *   AppContext by Introspector.flushCaches() was replaced with
                 *   ThreadGroupContext
                 * - others TBD
                 *
                 * From 1.7.0_25 onwards, a call to
                 * sun.awt.AppContext.getAppContext() results in a thread being
                 * started named AWT-AppKit that requires a graphic environment
                 * to be available.
                 */

                // Trigger a call to sun.awt.AppContext.getAppContext(). This
                // will pin the system class loader in memory but that shouldn't
                // be an issue.
                if (appContextProtection && !JreCompat.isJre8Available()) {
                    ImageIO.getCacheDirectory();
                }

                // Trigger the creation of the AWT (AWT-Windows, AWT-XAWT,
                // etc.) thread
                if (awtThreadProtection && !JreCompat.isJre9Available()) {
                    java.awt.Toolkit.getDefaultToolkit();
                }

                //开启gc守护线程保护 jre9不可用
                if (gcDaemonProtection && !JreCompat.isJre9Available()) {
                    try {
                        //获取GC类
                        Class<?> clazz = Class.forName("sun.misc.GC");
                        //获取GC类的requestLatency方法
                        Method method = clazz.getDeclaredMethod(
                                "requestLatency",
                                new Class[] {long.class});

                        //设置GC回收的时间
                        method.invoke(null, Long.valueOf(Long.MAX_VALUE - 1));
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        }
                    } catch (SecurityException | NoSuchMethodException | IllegalArgumentException |
                            IllegalAccessException e) {
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    } catch (InvocationTargetException e) {
                        ExceptionUtils.handleThrowable(e.getCause());
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    }
                }

                /*
                 * Calling getPolicy retains a static reference to the context
                 * class loader.
                 */
                if (securityPolicyProtection && !JreCompat.isJre8Available()) {
                    try {
                        // Policy.getPolicy();
                        Class<?> policyClass = Class
                                .forName("javax.security.auth.Policy");
                        Method method = policyClass.getMethod("getPolicy");
                        method.invoke(null);
                    } catch(ClassNotFoundException e) {
                        // Ignore. The class is deprecated.
                    } catch(SecurityException e) {
                        // Ignore. Don't need call to getPolicy() to be
                        // successful, just need to trigger static initializer.
                    } catch (NoSuchMethodException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (IllegalArgumentException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (IllegalAccessException e) {
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    } catch (InvocationTargetException e) {
                        ExceptionUtils.handleThrowable(e.getCause());
                        log.warn(sm.getString("jreLeakListener.authPolicyFail"),
                                e);
                    }
                }


                /*
                 * Initializing javax.security.auth.login.Configuration retains a static reference to the context
                 * class loader.
                 */
                if (securityLoginConfigurationProtection && !JreCompat.isJre8Available()) {
                    try {
                        Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
                    } catch(ClassNotFoundException e) {
                        // Ignore
                    }
                }

               //开启令牌轮训器保护
                if (tokenPollerProtection && !JreCompat.isJre9Available()) {
                    java.security.Security.getProviders();
                }

                /*
                 * Several components end up opening JarURLConnections without
                 * first disabling caching. This effectively locks the file.
                 * Whilst more noticeable and harder to ignore on Windows, it
                 * affects all operating systems.
                 *
                 * Those libraries/components known to trigger this issue
                 * include:
                 * - log4j versions 1.2.15 and earlier
                 * - javax.xml.bind.JAXBContext.newInstance()
                 *
                 * Java 9 onwards disables caching for JAR URLConnections
                 * Java 8 and earlier disables caching for all URLConnections
                 */

                //开启网址缓存保护
                if (urlCacheProtection) {
                    try {

                        //禁用URL缓存
                        JreCompat.getInstance().disableCachingForJarUrlConnections();
                    } catch (IOException e) {
                        log.error(sm.getString("jreLeakListener.jarUrlConnCacheFail"), e);
                    }
                }

                //开始xml解析保护
                if (xmlParsingProtection && !JreCompat.isJre9Available()) {
                    // There are two known issues with XML parsing that affect
                    // Java 7+. The issues both relate to cached Exception
                    // instances that retain a link to the TCCL via the
                    // backtrace field. Note that YourKit only shows this field
                    // when using the HPROF format memory snapshots.
                    // https://bz.apache.org/bugzilla/show_bug.cgi?id=58486
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
                        // Issue 1
                        // com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl
                        Document document = documentBuilder.newDocument();
                        document.createElement("dummy");
                        DOMImplementationLS implementation =
                                (DOMImplementationLS)document.getImplementation();
                        implementation.createLSSerializer().writeToString(document);
                        // Issue 1
                        // com.sun.org.apache.xerces.internal.dom.DOMNormalizer
                        document.normalize();
                    } catch (ParserConfigurationException e) {
                        //记录日志
                        log.error(sm.getString("jreLeakListener.xmlParseFail"),
                                e);
                    }
                }

                if (ldapPoolProtection && !JreCompat.isJre9Available()) {
                    try {
                        Class.forName("com.sun.jndi.ldap.LdapPoolManager");
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        }
                    }
                }

                /*
                 * Present in Java 7 onwards
                 * Work-around only available in Java 8.
                 * Fixed in Java 9 (from early access build 156)
                 */
                if (forkJoinCommonPoolProtection && JreCompat.isJre8Available() &&
                        !JreCompat.isJre9Available()) {
                    // Don't override any explicitly set property
                    if (System.getProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY) == null) {
                        System.setProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY,
                                SafeForkJoinWorkerThreadFactory.class.getName());
                    }
                }

                if (classesToInitialize != null) {
                    StringTokenizer strTok =
                        new StringTokenizer(classesToInitialize, ", \r\n\t");
                    while (strTok.hasMoreTokens()) {
                        String classNameToLoad = strTok.nextToken();
                        try {
                            Class.forName(classNameToLoad);
                        } catch (ClassNotFoundException e) {
                            log.error(
                                sm.getString("jreLeakListener.classToInitializeFail",
                                    classNameToLoad), e);
                            // continue with next class to load
                        }
                    }
                }

            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
        }
    }
}
