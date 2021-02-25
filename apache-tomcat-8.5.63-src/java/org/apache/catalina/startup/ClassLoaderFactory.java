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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 类加载器工厂类
 */
public final class ClassLoaderFactory {


    private static final Log log = LogFactory.getLog(ClassLoaderFactory.class);

    // --------------------------------------------------------- Public Methods


    /**
     * Create and return a new class loader, based on the configuration
     * defaults and the specified directory paths:
     *
     * @param unpacked Array of pathnames to unpacked directories that should
     *  be added to the repositories of the class loader, or <code>null</code>
     * for no unpacked directories to be considered
     * @param packed Array of pathnames to directories containing JAR files
     *  that should be added to the repositories of the class loader,
     * or <code>null</code> for no directories of JAR files to be considered
     * @param parent Parent class loader for the new class loader, or
     *  <code>null</code> for the system class loader.
     * @return the new class loader
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<>();

        // Add unpacked directories
        if (unpacked != null) {
            for (File file : unpacked) {
                if (!file.canRead()) {
                    continue;
                }
                file = new File(file.getCanonicalPath() + File.separator);
                URL url = file.toURI().toURL();
                if (log.isDebugEnabled()) {
                    log.debug("  Including directory " + url);
                }
                set.add(url);
            }
        }

        // Add packed directory JAR files
        if (packed != null) {
            for (File directory : packed) {
                if (!directory.isDirectory() || !directory.canRead()) {
                    continue;
                }
                String filenames[] = directory.list();
                if (filenames == null) {
                    continue;
                }
                for (String s : filenames) {
                    String filename = s.toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar")) {
                        continue;
                    }
                    File file = new File(directory, s);
                    if (log.isDebugEnabled()) {
                        log.debug("  Including jar file " + file.getAbsolutePath());
                    }
                    URL url = file.toURI().toURL();
                    set.add(url);
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[0]);
        return AccessController.doPrivileged(
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null) {
                            return new URLClassLoader(array);
                        } else {
                            return new URLClassLoader(array, parent);
                        }
                    }
                });
    }


    /**
     * 生产类加载器 URLClassLoader
     * @param repositories 资源对象列表
     * @param parent 父加载器
     * @return
     * @throws Exception
     */
    public static ClassLoader createClassLoader(List<Repository> repositories,
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())//打印日志
            log.debug("Creating new class loader");

        // 可通过url反问的资源集合
        Set<URL> set = new LinkedHashSet<>();

        if (repositories != null) {//存在资源
            for (Repository repository : repositories)  {//遍历资源
                if (repository.getType() == RepositoryType.URL) {//可以通过URL对象反问的资源
                    //创建URL
                    URL url = buildClassLoaderUrl(repository.getLocation());
                    if (log.isDebugEnabled())//打印日志
                        log.debug("  Including URL " + url);
                    //添加到资源里诶包
                    set.add(url);
                } else if (repository.getType() == RepositoryType.DIR) {//文件夹类型
                    //文件夹类型  实例化一个文件夹对象
                    File directory = new File(repository.getLocation());
                    //获取去掉.之后的绝对路径对象
                    directory = directory.getCanonicalFile();
                    //校验文件类型是否为文件夹
                    if (!validateFile(directory, RepositoryType.DIR)) {
                        continue;
                    }

                    //将文件夹对应的url对象添加到url列表
                    URL url = buildClassLoaderUrl(directory);
                    if (log.isDebugEnabled())//带你日志
                        log.debug("  Including directory " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.JAR) {//jar类型的文件
                    File file=new File(repository.getLocation());
                    file = file.getCanonicalFile();
                    if (!validateFile(file, RepositoryType.JAR)) {
                        continue;
                    }
                    //创建url
                    URL url = buildClassLoaderUrl(file);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + url);
                    //添加到列表
                    set.add(url);
                } else if (repository.getType() == RepositoryType.GLOB) {//某个文件夹下某种类型的所有文件
                    //获取文件夹
                    File directory=new File(repository.getLocation());
                    //绝对路径文件夹对
                    directory = directory.getCanonicalFile();
                    //校验文件夹格式
                    if (!validateFile(directory, RepositoryType.GLOB)) {
                        continue;
                    }
                    if (log.isDebugEnabled())
                        log.debug("  Including directory glob "
                            + directory.getAbsolutePath());
                    //获取文件夹下所有的文件
                    String filenames[] = directory.list();
                    if (filenames == null) {
                        continue;
                    }
                    for (String s : filenames) {//遍历文件夹下所有的文件
                        //获取文件名小写格式
                        String filename = s.toLowerCase(Locale.ENGLISH);
                        if (!filename.endsWith(".jar"))//过滤掉不是.jar类型的文件
                            continue;
                        //实例化文件对象
                        File file = new File(directory, s);
                        //获取绝对路径
                        file = file.getCanonicalFile();
                        if (!validateFile(file, RepositoryType.JAR)) {//校验文件为.jar类型
                            continue;
                        }
                        if (log.isDebugEnabled())
                            log.debug("    Including glob jar file "
                                    + file.getAbsolutePath());
                        URL url = buildClassLoaderUrl(file);
                        //添加到url列表
                        set.add(url);
                    }
                }
            }
        }

        // 将url列表转为URL路径
        final URL[] array = set.toArray(new URL[0]);
        if (log.isDebugEnabled())//打印日志
            for (int i = 0; i < array.length; i++) {
                log.debug("  location " + i + " is " + array[i]);
            }

        return AccessController.doPrivileged(//调用系统权限 执行代码
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null)//没有指定符加载器
                            //实例化URLClassLoader加载器 返回
                            return new URLClassLoader(array);
                        else
                            return new URLClassLoader(array, parent);
                    }
                });
    }

    /**
     * 验证文件的类型
     * @param file 文件对象
     * @param type 期待的类型
     * @return
     * @throws IOException
     */
    private static boolean validateFile(File file,
            RepositoryType type) throws IOException {
        if (RepositoryType.DIR == type || RepositoryType.GLOB == type) {//期待的类型问文件夹  获取是文件夹下某种类型的比如.jar文件的对象
            if (!file.isDirectory() || !file.canRead()) {//文件不是文件夹 或者是文件不可读
                //打印错误消息
                String msg = "Problem with directory [" + file +
                        "], exists: [" + file.exists() +
                        "], isDirectory: [" + file.isDirectory() +
                        "], canRead: [" + file.canRead() + "]";

                //实例化e:\learn\tomcat\catalina-home文件对象
                File home = new File (Bootstrap.getCatalinaHome());
                //获取去掉.之后的绝对路径文件
                home = home.getCanonicalFile();
                //实例化e:\learn\tomcat\catalina-home文件对象
                File base = new File (Bootstrap.getCatalinaBase());
                //获取去掉.之后的绝对路径文件
                base = base.getCanonicalFile();
                //实例化一个默认的文件 e:\lean\tomcat\catalina-home\lib
                File defaultValue = new File(base, "lib");

                // Existence of ${catalina.base}/lib directory is optional.
                // Hide the warning if Tomcat runs with separate catalina.home
                // and catalina.base and that directory is absent.
                if (!home.getPath().equals(base.getPath())
                        && file.getPath().equals(defaultValue.getPath())
                        && !file.exists()) {
                    log.debug(msg);
                } else {
                    log.warn(msg);
                }
                return false;
            }
        } else if (RepositoryType.JAR == type) {//为jar类型的资源
            if (!file.canRead()) {//不可读
                log.warn("Problem with JAR file [" + file +
                        "], exists: [" + file.exists() +
                        "], canRead: [" + file.canRead() + "]");
                return false;
            }
        }
        return true;
    }


    /**
     * 通过加载器指定的路径来创建加载器
     * @param urlString 指定的路径
     * @return
     * @throws MalformedURLException
     */
    private static URL buildClassLoaderUrl(String urlString) throws MalformedURLException {
        // URLs passed to class loaders may point to directories that contain
        // JARs. If these URLs are used to construct URLs for resources in a JAR
        // the URL will be used as is. It is therefore necessary to ensure that
        // the sequence "!/" is not present in a class loader URL.
        String result = urlString.replaceAll("!/", "%21/");

        //创建url
        return new URL(result);
    }


    /**
     * 根据文件对象 创建类加载器的url对象
     * @param file 文件对象
     * @return
     * @throws MalformedURLException
     */
    private static URL buildClassLoaderUrl(File file) throws MalformedURLException {
        // 文件url字符串格式
        String fileUrlString = file.toURI().toString();
        //替换其他字符
        fileUrlString = fileUrlString.replaceAll("!/", "%21/");
        //返回url
        return new URL(fileUrlString);
    }


    /**
     * 资源类型枚举
     */
    public enum RepositoryType {
        DIR,//文件件
        GLOB,//某个文件夹下某种资源的文件比如:*.jar文件
        JAR,//.jar文件
        URL//url路径资源
    }

    /**
     * 资源对象
     */
    public static class Repository {

        /**
         * 资源位置
         */
        private final String location;

        /**
         * 资源类型
         */
        private final RepositoryType type;

        /**
         * 实例化一个资源
         * @param location 路径
         * @param type 资源类型
         */
        public Repository(String location, RepositoryType type) {
            //路径
            this.location = location;
            //类型
            this.type = type;
        }

        public String getLocation() {
            return location;
        }

        public RepositoryType getType() {
            return type;
        }
    }
}
