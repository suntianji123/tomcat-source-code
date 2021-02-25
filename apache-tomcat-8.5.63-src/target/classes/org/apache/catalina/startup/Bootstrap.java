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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.ClassLoaderFactory.Repository;
import org.apache.catalina.startup.ClassLoaderFactory.RepositoryType;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Bootstrap loader for Catalina.  This application constructs a class loader
 * for use in loading the Catalina internal classes (by accumulating all of the
 * JAR files found in the "server" directory under "catalina.home"), and
 * starts the regular execution of the container.  The purpose of this
 * roundabout approach is to keep the Catalina internal classes (and any
 * other classes they depend on, such as an XML parser) out of the system
 * class path and therefore not visible to application level classes.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public final class Bootstrap {

    private static final Log log = LogFactory.getLog(Bootstrap.class);

    /**
     * 启动配置的实例对象的锁
     */
    private static final Object daemonLock = new Object();

    /**
     * 启动配置的实例对象
     */
    private static volatile Bootstrap daemon = null;

    /**
     * catalina-base文件（默认与catalina-home文件为同一个文件）
     */
    private static final File catalinaBaseFile;

    /**
     * catalina-home文件夹文件
     */
    private static final File catalinaHomeFile;

    /**
     * "${catalina.base}/lib","${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
     * 字符串匹配器
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("(\"[^\"]*\")|(([^,])*)");

    static {
        //获取当前运行的目录 E:\learn\tomcat
        String userDir = System.getProperty("user.dir");

        // 获取jvm参数catalina.home的路径：catalina-home
        String home = System.getProperty(Constants.CATALINA_HOME_PROP);
        File homeFile = null;

        if (home != null) {
            //实例化一个 catalina-home文件
            File f = new File(home);
            try {
                //获取去掉相对路径的绝对路径文件  E：\learn\tomcat\catalina-home
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                //获取绝对路径文件
                homeFile = f.getAbsoluteFile();
            }
        }

        if (homeFile == null) {
            // First fall-back. See if current directory is a bin directory
            // in a normal Tomcat install
            File bootstrapJar = new File(userDir, "bootstrap.jar");

            if (bootstrapJar.exists()) {
                File f = new File(userDir, "..");
                try {
                    homeFile = f.getCanonicalFile();
                } catch (IOException ioe) {
                    homeFile = f.getAbsoluteFile();
                }
            }
        }

        if (homeFile == null) {
            // Second fall-back. Use current directory
            File f = new File(userDir);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        //指定catalina-home文件
        catalinaHomeFile = homeFile;
        //设置jvm系统参数 -Dcatalina.home=catalina-home文件的绝对路径
        System.setProperty(
                Constants.CATALINA_HOME_PROP, catalinaHomeFile.getPath());

        // 获取jvm系统参数 -Dcatalina.base=catalina-home文件的绝对路径
        String base = System.getProperty(Constants.CATALINA_BASE_PROP);
        if (base == null) {//基本路径不存在
            //设置catalina-home 与catalina-home指定同一个文件
            catalinaBaseFile = catalinaHomeFile;
        } else {
            File baseFile = new File(base);
            try {
                baseFile = baseFile.getCanonicalFile();
            } catch (IOException ioe) {
                baseFile = baseFile.getAbsoluteFile();
            }
            catalinaBaseFile = baseFile;
        }

        //设置jvm系统参数:-Dcatalina-base=catalina-home文件绝对的路径
        System.setProperty(
                Constants.CATALINA_BASE_PROP, catalinaBaseFile.getPath());
    }

    // -------------------------------------------------------------- Variables


    /**
     * Catalina对象 启动对象 设置parentClassLoader为shareLoader
     */
    private Object catalinaDaemon = null;

    /**
     * 公共的类加载器 URLClassLoader
     * 加载 "${catalina.base}/lib","${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
     */
    ClassLoader commonLoader = null;

    /**
     * catalina server类加载器 继承commonLoader
     */
    ClassLoader catalinaLoader = null;

    /**
     * 共享的类加载器 继承commonLoader
     */
    ClassLoader sharedLoader = null;


    // -------------------------------------------------------- Private Methods


    /**
     * 初始化类加载器
     */
    private void initClassLoaders() {
        try {
            //创建公共的类加载器 URLClassLoader
            commonLoader = createClassLoader("common", null);
            if (commonLoader == null) {//公共的classLoader
                // no config file, default to this loader - we might be in a 'single' env.
                commonLoader = this.getClass().getClassLoader();
            }

            //创建server类加载器
            catalinaLoader = createClassLoader("server", commonLoader);

            //共享的类加载器
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            //处理异常
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            //退出程序
            System.exit(1);
        }
    }


    /**
     * 创建类加载器
     * @param name 类加载器的名字
     * @param parent 加药被创建的额类加载器的父类
     * @return
     * @throws Exception
     */
    private ClassLoader createClassLoader(String name, ClassLoader parent)
        throws Exception {
        //从catalina-home/conf/catalina.properties中获取属性值  例如common.loader的属性值
        String value = CatalinaProperties.getProperty(name + ".loader");
        //属性值不存在 或者为""字符串 直接返回父加载器
        if ((value == null) || (value.equals("")))
            return parent;

        //替换掉属性值字符串中的${}参数值 用系统的jvm参数值来替换
        value = replace(value);

        //实例化一个资源List
        List<Repository> repositories = new ArrayList<>();

        //获取属性值的资源路径数组
        String[] repositoryPaths = getPaths(value);

        for (String repository : repositoryPaths) {//遍历资源路径
            // Check for a JAR URL repository
            try {
                //实例化一个Url对象
                @SuppressWarnings("unused")
                URL url = new URL(repository);

                //实例化一个资源对象 将资源对象添加到资源列表
                repositories.add(new Repository(repository, RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            //资源不能通过url访问
            // Local repository
            if (repository.endsWith("*.jar")) {//文件夹下所有的.jar文件
                //文件路径
                repository = repository.substring
                    (0, repository.length() - "*.jar".length());
                repositories.add(new Repository(repository, RepositoryType.GLOB));
            } else if (repository.endsWith(".jar")) {//单个.jar文件
                //设置.jar文件
                repositories.add(new Repository(repository, RepositoryType.JAR));
            } else {
                //文件夹
                repositories.add(new Repository(repository, RepositoryType.DIR));
            }
        }

        //创建类加载器 URLClassLoader类加载器
        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }


    /**
     * 替换字符串 例如"${catalina.base}/lib","${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
     * 将${}中的系统参数用参数值来替换 返回新的字符串
     * @param str 将要被替换的字符串
     * @return
     */
    protected String replace(String str) {
        //定义结果字符串
        String result = str;
        //获取第一个${下标
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {//${下标存在
            //实例化一个StringBuilder对象
            StringBuilder builder = new StringBuilder();
            //}位置
            int pos_end = -1;
            while (pos_start >= 0) {//从第一个${下标开始往后遍历
                //拼接 ${之前的字符串
                builder.append(str, pos_end + 1, pos_start);
                //获取之后的}位置
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {//结束位置不存在 字符串有误
                    pos_end = pos_start - 1;
                    break;
                }

                //获取将要被替换的属性名  例如catalina.base
                String propName = str.substring(pos_start + 2, pos_end);
                //替换属性的属性值
                String replacement;
                if (propName.length() == 0) {//属性名为空字符串
                    //替换的属性值为null
                    replacement = null;
                } else if (Constants.CATALINA_HOME_PROP.equals(propName)) {//替换的属性名为catalina.home
                    //设置替换的属性值为 e:\learn\tomcat\catalina-home
                    replacement = getCatalinaHome();
                } else if (Constants.CATALINA_BASE_PROP.equals(propName)) {//替换的属性名为catalina.base
                    //设置替换的属性值为 e:\learn\tomacat\catalina-home
                    replacement = getCatalinaBase();
                } else {
                    //从jvm系统参数中获取参数值
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {//存在属性值
                    //拼接属性值
                    builder.append(replacement);
                } else {
                    //不存在属性值 继续拼接
                    builder.append(str, pos_start, pos_end + 1);
                }
                //设置起始位置
                pos_start = str.indexOf("${", pos_end + 1);
            }

            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }

        //返回替换掉${}中参数之后的新的字符串
        return result;
    }


    /**
     * 初始化启动配置
     * @throws Exception
     */
    public void init() throws Exception {

        //初始哈类加载器 common urlclasssLoader
        initClassLoaders();

        //设置当前线程的class类加载器
        Thread.currentThread().setContextClassLoader(catalinaLoader);

        SecurityClassLoad.securityClassLoad(catalinaLoader);

        // Load our startup class and call its process() method
        if (log.isDebugEnabled())//打印日志
            log.debug("Loading startup class");

        //加载org.apache.catalina.startup.Catalina类到虚拟机
        Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
        //实例化一个启动类对象
        Object startupInstance = startupClass.getConstructor().newInstance();

        // Set the shared extensions class loader
        if (log.isDebugEnabled())
            log.debug("Setting startup class properties");

        //setParentClassLoader 方法名
        String methodName = "setParentClassLoader";
        //实例化一个参数数组
        Class<?> paramTypes[] = new Class[1];
        //设置参数类型数的第一个元素为classLoader
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        //实例化一个参数值数组
        Object paramValues[] = new Object[1];
        //参数值数组的第一个元素为共享的classLoader
        paramValues[0] = sharedLoader;
        //获取方法
        Method method =
            startupInstance.getClass().getMethod(methodName, paramTypes);
        //执行方法 设置父classLoader
        method.invoke(startupInstance, paramValues);

        //设置启动对象
        catalinaDaemon = startupInstance;
    }


    /**
     * 启动配置对象加载启动main方法的参数数组
     * @param arguments 启动main方法的参数数组
     * @throws Exception
     */
    private void load(String[] arguments) throws Exception {

        //方法名
        String methodName = "load";
        //参数值数组
        Object param[];
        //参数类型数组
        Class<?> paramTypes[];
        if (arguments==null || arguments.length==0) {//没有闯入参数
            paramTypes = null;
            param = null;
        } else {
            //实例化参数类型数组
            paramTypes = new Class[1];
            //设置参数类型数组的第0个元素的类型
            paramTypes[0] = arguments.getClass();
            //实例化参数值数组
            param = new Object[1];
            //设置参数值数组的第0个元素的值为传入参数值
            param[0] = arguments;
        }
        //获取load方法
        Method method =
            catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled()) {
            log.debug("Calling startup class " + method);
        }

        //执行Catalina对象的load方法
        method.invoke(catalinaDaemon, param);
    }


    /**
     * getServer() for configtest
     */
    private Object getServer() throws Exception {

        String methodName = "getServer";
        Method method = catalinaDaemon.getClass().getMethod(methodName);
        return method.invoke(catalinaDaemon);
    }


    // ----------------------------------------------------------- Main Program


    /**
     * Load the Catalina daemon.
     * @param arguments Initialization arguments
     * @throws Exception Fatal initialization error
     */
    public void init(String[] arguments) throws Exception {

        init();
        load(arguments);
    }


    /**
     * Start the Catalina daemon.
     * @throws Exception Fatal start error
     */
    public void start() throws Exception {
        if (catalinaDaemon == null) {
            init();
        }

        Method method = catalinaDaemon.getClass().getMethod("start", (Class [])null);
        method.invoke(catalinaDaemon, (Object [])null);
    }


    /**
     * Stop the Catalina Daemon.
     * @throws Exception Fatal stop error
     */
    public void stop() throws Exception {
        Method method = catalinaDaemon.getClass().getMethod("stop", (Class []) null);
        method.invoke(catalinaDaemon, (Object []) null);
    }


    /**
     * Stop the standalone server.
     * @throws Exception Fatal stop error
     */
    public void stopServer() throws Exception {

        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", (Class []) null);
        method.invoke(catalinaDaemon, (Object []) null);
    }


   /**
     * Stop the standalone server.
     * @param arguments Command line arguments
     * @throws Exception Fatal stop error
     */
    public void stopServer(String[] arguments) throws Exception {

        Object param[];
        Class<?> paramTypes[];
        if (arguments == null || arguments.length == 0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);
    }


    /**
     * Set flag.
     * @param await <code>true</code> if the daemon should block
     * @throws Exception Reflection error
     */
    public void setAwait(boolean await)
        throws Exception {

        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object paramValues[] = new Object[1];
        paramValues[0] = Boolean.valueOf(await);
        Method method =
            catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);
    }

    public boolean getAwait() throws Exception {
        Class<?> paramTypes[] = new Class[0];
        Object paramValues[] = new Object[0];
        Method method =
            catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b=(Boolean)method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }


    /**
     * Destroy the Catalina Daemon.
     */
    public void destroy() {

        // FIXME

    }


    /**
     * Main method and entry point when starting Tomcat via the provided
     * scripts.
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {

        synchronized (daemonLock) {//加锁
            if (daemon == null) {//启动配置的实例对象为null  没有创建过启动配置的实例对象
                // 实例化一个启动配置的实例对象
                Bootstrap bootstrap = new Bootstrap();
                try {
                    //初始化启动配置的实例对象
                    //设置commonLoader sharedLoader CatalianLoader
                    bootstrap.init();
                } catch (Throwable t) {
                    handleThrowable(t);
                    t.printStackTrace();
                    return;
                }

                //设置bootsrap启动配置对象
                daemon = bootstrap;
            } else {
                // When running as a service the call to stop will be on a new
                // thread so make sure the correct class loader is used to
                // prevent a range of class not found exceptions.
                Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
            }
        }

        try {

            //命令为start
            String command = "start";
            if (args.length > 0) {//启动参数
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {//启动
                //设置最后一个参数为start
                args[args.length - 1] = "start";
                //加载参数
                daemon.load(args);

                //启动配置
                daemon.start();
            } else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                daemon.setAwait(true);
                daemon.load(args);
                daemon.start();
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
                System.exit(0);
            } else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            // Unwrap the Exception for clearer error reporting
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            handleThrowable(t);
            t.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Obtain the name of configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     * @return the catalina home
     */
    public static String getCatalinaHome() {
        return catalinaHomeFile.getPath();
    }


    /**
     * Obtain the name of the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHome()} will be used.
     * @return the catalina base
     */
    public static String getCatalinaBase() {
        return catalinaBaseFile.getPath();
    }


    /**
     * Obtain the configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     * @return the catalina home as a file
     */
    public static File getCatalinaHomeFile() {
        return catalinaHomeFile;
    }


    /**
     * Obtain the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHomeFile()} will be used.
     * @return the catalina base as a file
     */
    public static File getCatalinaBaseFile() {
        return catalinaBaseFile;
    }


    // Copied from ExceptionUtils since that class is not visible during start
    static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof StackOverflowError) {
            // Swallow silently - it should be recoverable
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    // Copied from ExceptionUtils so that there is no dependency on utils
    static Throwable unwrapInvocationTargetException(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * 获取字符串资源的路径 比如："E:/learn/tomcat/catalina-home/lib","E:/learn/tomcat/catalina-home/lib/*.jar","E:/learn/tomcat/catalina-home/lib","E:/learn/tomcat/catalina-home/lib/*.jar"
     * @param value 返回字符串中路径集合
     * @return
     */
    protected static String[] getPaths(String value) {

        //实例化一个结果数组
        List<String> result = new ArrayList<>();

        //获取字符串的配置器
        Matcher matcher = PATH_PATTERN.matcher(value);

        while (matcher.find()) {//遍历匹配到的结果字符串组
            //获取匹配到的字符串 : E:/learn/tomcat/catalina-home/lib
            String path = value.substring(matcher.start(), matcher.end());

            //去除路径前后空格
            path = path.trim();
            if (path.length() == 0) {//过滤掉空字符串
                continue;
            }

            //获取第一个字符 "
            char first = path.charAt(0);
            //字后一个字符 "
            char last = path.charAt(path.length() - 1);

            if (first == '"' && last == '"' && path.length() > 1) {//""包裹的路径
                //设置路径的内容为""包裹中的字符串
                path = path.substring(1, path.length() - 1);
                //去除路径前后空格
                path = path.trim();
                if (path.length() == 0) {//过滤掉空字符串
                    continue;
                }
            } else if (path.contains("\"")) {//路径中包括 " 说明路径非法
                // Unbalanced quotes
                // Too early to use standard i18n support. The class path hasn't
                // been configured.
                throw new IllegalArgumentException(
                        "The double quote [\"] character can only be used to quote paths. It must " +
                        "not appear in a path. This loader path is not valid: [" + value + "]");
            } else {
                // Not quoted - NO-OP
            }

            //解析出的路径添加到结果列表
            result.add(path);
        }

        //将结果哦列表转为字符串数组
        return result.toArray(new String[0]);
    }
}
