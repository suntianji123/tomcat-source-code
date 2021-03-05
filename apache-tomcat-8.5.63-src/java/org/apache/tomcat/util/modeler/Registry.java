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
package org.apache.tomcat.util.modeler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.modules.ModelerSource;
import org.apache.tomcat.util.res.StringManager;

/**
 * 访问MBeanServer仓库的工具类
 */
public class Registry implements RegistryMBean, MBeanRegistration {

    /**
     * The Log instance to which we will write our log messages.
     */
    private static final Log log = LogFactory.getLog(Registry.class);
    private static final StringManager sm = StringManager.getManager(Registry.class);

    // Support for the factory methods

    /**
     * 单例对象
     */
    private static Registry registry = null;

    // Per registry fields

    /**
     * 管理MBean的仓库对象
     */
    private volatile MBeanServer server = null;

    /**
     * 访问MBeanServer仓库的锁对象
     */
    private final Object serverLock = new Object();

    /**
     * name | ManagedBean表
     */
    private HashMap<String,ManagedBean> descriptors = new HashMap<>();

    /**
     * bean的全类名 | ManagedBean表
     */
    private HashMap<String,ManagedBean> descriptorsByClass = new HashMap<>();

    /**
     * 已经搜素过的路径 与 URL之前的映射关系map
     * 某个包下的mbeans-descriptors.xml 是否已经被解析过
     */
    private HashMap<String,URL> searchedPaths = new HashMap<>();

    /**
     * 登记处的警卫对象
     */
    private Object guard;

    // Id - small ints to use array access. No reset on stop()
    // Used for notifications
    private final Hashtable<String,Hashtable<String,Integer>> idDomains =
        new Hashtable<>();
    private final Hashtable<String,int[]> ids = new Hashtable<>();


    // ----------------------------------------------------------- Constructors

    /**
     * 实例化一个登记处对象
     */
     public Registry() {
        super();
    }

    // -------------------- Static methods  --------------------
    // Factories

    /**
     * 获取登记处对象
     * @param key
     * @param guard 登记处的警卫
     * @return
     */
    public static synchronized Registry getRegistry(Object key, Object guard) {
        if (registry == null) {//登记处对象还没创建
            //实例化一个登记处对象
            registry = new Registry();
        }

        //登记处已经有警卫  但是登记处的警卫与指定的警卫不是同一人
        if (registry.guard != null && registry.guard != guard) {
            return null;
        }

        //返回警卫
        return registry;
    }


    /** Lifecycle method - clean up the registry metadata.
     *  Called from resetMetadata().
     *
     * @since 1.1
     */
    @Override
    public void stop() {
        descriptorsByClass = new HashMap<>();
        descriptors = new HashMap<>();
        searchedPaths = new HashMap<>();
    }


    /**
     * Register a bean by creating a modeler mbean and adding it to the
     * MBeanServer.
     *
     * If metadata is not loaded, we'll look up and read a file named
     * "mbeans-descriptors.ser" or "mbeans-descriptors.xml" in the same package
     * or parent.
     *
     * If the bean is an instance of DynamicMBean. it's metadata will be
     * converted to a model mbean and we'll wrap it - so modeler services will
     * be supported
     *
     * If the metadata is still not found, introspection will be used to extract
     * it automatically.
     *
     * If an mbean is already registered under this name, it'll be first
     * unregistered.
     *
     * If the component implements MBeanRegistration, the methods will be
     * called. If the method has a method "setRegistry" that takes a
     * RegistryMBean as parameter, it'll be called with the current registry.
     *
     *
     * @param bean Object to be registered
     * @param oname Name used for registration
     * @param type The type of the mbean, as declared in mbeans-descriptors. If
     *            null, the name of the class will be used. This can be used as
     *            a hint or by subclasses.
     * @throws Exception if a registration error occurred
     * @since 1.1
     */
    @Override
    public void registerComponent(Object bean, String oname, String type) throws Exception {
        registerComponent(bean, new ObjectName(oname), type);
    }


    /**
     * Unregister a component. We'll first check if it is registered, and mask
     * all errors. This is mostly a helper.
     *
     * @param oname Name used for unregistration
     *
     * @since 1.1
     */
    @Override
    public void unregisterComponent(String oname) {
        try {
            unregisterComponent(new ObjectName(oname));
        } catch (MalformedObjectNameException e) {
            log.info("Error creating object name " + e );
        }
    }


    /**
     * Invoke a operation on a list of mbeans. Can be used to implement
     * lifecycle operations.
     *
     * @param mbeans list of ObjectName on which we'll invoke the operations
     * @param operation  Name of the operation ( init, start, stop, etc)
     * @param failFirst  If false, exceptions will be ignored
     * @throws Exception Error invoking operation
     * @since 1.1
     */
    @Override
    public void invoke(List<ObjectName> mbeans, String operation, boolean failFirst)
            throws Exception {

        if (mbeans == null) {
            return;
        }
        for (ObjectName current : mbeans) {
            try {
                if (current == null) {
                    continue;
                }
                if (getMethodInfo(current, operation) == null) {
                    continue;
                }
                getMBeanServer().invoke(current, operation, new Object[] {}, new String[] {});

            } catch (Exception t) {
                if (failFirst)
                    throw t;
                log.info("Error initializing " + current + " " + t.toString());
            }
        }
    }

    // -------------------- ID registry --------------------

    /**
     * Return an int ID for faster access. Will be used for notifications
     * and for other operations we want to optimize.
     *
     * @param domain Namespace
     * @param name Type of the notification
     * @return A unique id for the domain:name combination
     * @since 1.1
     */
    @Override
    public synchronized int getId(String domain, String name) {
        if (domain == null) {
            domain = "";
        }
        Hashtable<String, Integer> domainTable = idDomains.get(domain);
        if (domainTable == null) {
            domainTable = new Hashtable<>();
            idDomains.put(domain, domainTable);
        }
        if (name == null) {
            name = "";
        }
        Integer i = domainTable.get(name);

        if (i != null) {
            return i.intValue();
        }

        int id[] = ids.get(domain);
        if (id == null) {
            id = new int[1];
            ids.put(domain, id);
        }
        int code = id[0]++;
        domainTable.put(name, Integer.valueOf(code));
        return code;
    }


    /**
     * 注册ManagedBean对象
     * @param bean 将要被注册的ManagedBean对象
     */
    public void addManagedBean(ManagedBean bean) {
        //将MBean对象 放入描述器表
        descriptors.put(bean.getName(), bean);
        if (bean.getType() != null) {//指定了描述的class类型
            descriptorsByClass.put(bean.getType(), bean);
        }
    }


    /**
     * 根据全类名获取可管理的bean对象
     * @param name 全类名
     * @return
     */
    public ManagedBean findManagedBean(String name) {
       //从描述器中获取可管理的bean对象
        ManagedBean mb = descriptors.get(name);
        if (mb == null)//不存在可管理的bean对象

            mb = descriptorsByClass.get(name);
        return mb;
    }


    // -------------------- Helpers --------------------

    /**
     * Get the type of an attribute of the object, from the metadata.
     *
     * @param oname The bean name
     * @param attName The attribute name
     * @return null if metadata about the attribute is not found
     * @since 1.1
     */
    public String getType(ObjectName oname, String attName) {
        String type = null;
        MBeanInfo info = null;
        try {
            info = getMBeanServer().getMBeanInfo(oname);
        } catch (Exception e) {
            log.info( "Can't find metadata for object" + oname );
            return null;
        }

        MBeanAttributeInfo attInfo[] = info.getAttributes();
        for (MBeanAttributeInfo mBeanAttributeInfo : attInfo) {
            if (attName.equals(mBeanAttributeInfo.getName())) {
                type = mBeanAttributeInfo.getType();
                return type;
            }
        }
        return null;
    }


    /**
     * Find the operation info for a method
     *
     * @param oname The bean name
     * @param opName The operation name
     * @return the operation info for the specified operation
     */
    public MBeanOperationInfo getMethodInfo(ObjectName oname, String opName) {
        MBeanInfo info = null;
        try {
            info = getMBeanServer().getMBeanInfo(oname);
        } catch (Exception e) {
            log.info( "Can't find metadata " + oname );
            return null;
        }
        MBeanOperationInfo attInfo[] = info.getOperations();
        for (MBeanOperationInfo mBeanOperationInfo : attInfo) {
            if (opName.equals(mBeanOperationInfo.getName())) {
                return mBeanOperationInfo;
            }
        }
        return null;
    }

    /**
     * Find the operation info for a method.
     *
     * @param oname The bean name
     * @param opName The operation name
     * @param argCount The number of arguments to the method
     * @return the operation info for the specified operation
     * @throws InstanceNotFoundException If the object name is not bound to an MBean
     */
    public MBeanOperationInfo getMethodInfo(ObjectName oname, String opName, int argCount)
        throws InstanceNotFoundException
    {
        MBeanInfo info = null;
        try {
            info = getMBeanServer().getMBeanInfo(oname);
        } catch (InstanceNotFoundException infe) {
            throw infe;
        } catch (Exception e) {
            log.warn(sm.getString("registry.noMetadata", oname), e);
            return null;
        }
        MBeanOperationInfo attInfo[] = info.getOperations();
        for (MBeanOperationInfo mBeanOperationInfo : attInfo) {
            if (opName.equals(mBeanOperationInfo.getName())
                    && argCount == mBeanOperationInfo.getSignature().length) {
                return mBeanOperationInfo;
            }
        }
        return null;
    }

    /**
     * Unregister a component. This is just a helper that avoids exceptions by
     * checking if the mbean is already registered
     *
     * @param oname The bean name
     */
    public void unregisterComponent(ObjectName oname) {
        try {
            if (oname != null && getMBeanServer().isRegistered(oname)) {
                getMBeanServer().unregisterMBean(oname);
            }
        } catch (Throwable t) {
            log.error("Error unregistering mbean", t);
        }
    }


    /**
     * 获取管理MBean的仓库
     * @return
     */
    public MBeanServer getMBeanServer() {
        if (server == null) {//服务器对象为null
            synchronized (serverLock) {
                if (server == null) {//服务器你对象
                    //当前时间
                    long t1 = System.currentTimeMillis();
                    //查找MBeanServer
                    if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
                        //第0个元素为MBeanServer
                        server = MBeanServerFactory.findMBeanServer(null).get(0);
                        if (log.isDebugEnabled()) {
                            log.debug("Using existing MBeanServer " + (System.currentTimeMillis() - t1));
                        }
                    } else {
                        //获取平台的MBeanServer对象
                        server = ManagementFactory.getPlatformMBeanServer();
                        if (log.isDebugEnabled()) {
                            log.debug("Creating MBeanServer" + (System.currentTimeMillis() - t1));
                        }
                    }
                }
            }
        }

        //返回管理MBean的仓库MBeanServer对象
        return server;
    }


    /**
     * 查找可管理的Bean对象
     * @param bean bean对象
     * @param beanClass bean的class类
     * @param type  bean的全类名
     * @return
     * @throws Exception
     */
    public ManagedBean findManagedBean(Object bean, Class<?> beanClass, String type)
            throws Exception {

        if (bean != null && beanClass == null) {//如果没有指定bean的class类型
            //从bean对象获取class类
            beanClass = bean.getClass();
        }

        if (type == null) {//全类名不存在
            //获取bean的全类名
            type = beanClass.getName();
        }

        // 通过对象的全类名查找对象的ManagedBean描述信息对象
        ManagedBean managed = findManagedBean(type);

        // Search for a descriptor in the same package
        if (managed == null) {
            // check package and parent packages
            if (log.isDebugEnabled()) {
                log.debug("Looking for descriptor ");
            }
            findDescriptor(beanClass, type);

            managed = findManagedBean(type);
        }

        // Still not found - use introspection
        if (managed == null) {
            if (log.isDebugEnabled()) {
                log.debug("Introspecting ");
            }

            // introspection
            load("MbeansDescriptorsIntrospectionSource", beanClass, type);

            managed = findManagedBean(type);
            if (managed == null) {
                log.warn( "No metadata found for " + type );
                return null;
            }
            managed.setName(type);
            addManagedBean(managed);
        }

        //返回对象的ManagedBean描述对象
        return managed;
    }


    /**
     * EXPERIMENTAL Convert a string to object, based on type. Used by several
     * components. We could provide some pluggability. It is here to keep things
     * consistent and avoid duplication in other tasks
     *
     * @param type Fully qualified class name of the resulting value
     * @param value String value to be converted
     * @return Converted value
     */
    public Object convertValue(String type, String value) {
        Object objValue = value;

        if (type == null || "java.lang.String".equals(type)) {
            // string is default
            objValue = value;
        } else if ("javax.management.ObjectName".equals(type) || "ObjectName".equals(type)) {
            try {
                objValue = new ObjectName(value);
            } catch (MalformedObjectNameException e) {
                return null;
            }
        } else if ("java.lang.Integer".equals(type) || "int".equals(type)) {
            objValue = Integer.valueOf(value);
        } else if ("java.lang.Long".equals(type) || "long".equals(type)) {
            objValue = Long.valueOf(value);
        } else if ("java.lang.Boolean".equals(type) || "boolean".equals(type)) {
            objValue = Boolean.valueOf(value);
        }
        return objValue;
    }


    /**
     * 在加载资源
     * @param sourceType 资源类型
     * @param source 资源url对象
     * @param param 参数
     * @return
     * @throws Exception
     */
    public List<ObjectName> load(String sourceType, Object source, String param) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("load " + source);
        }
        String location = null;
        String type = null;
        Object inputsource = null;

        if (source instanceof URL) {//如果资源是url
            //将资源转为url类型
            URL url = (URL) source;
            //获取路径
            location = url.toString();
            //类型为参数
            type = param;
            //打开输入流
            inputsource = url.openStream();
            if (sourceType == null && location.endsWith(".xml")) {
                sourceType = "MbeansDescriptorsDigesterSource";
            }
        } else if (source instanceof File) {
            location = ((File) source).getAbsolutePath();
            inputsource = new FileInputStream((File) source);
            type = param;
            if (sourceType == null && location.endsWith(".xml")) {
                sourceType = "MbeansDescriptorsDigesterSource";
            }
        } else if (source instanceof InputStream) {
            type = param;
            inputsource = source;
        } else if (source instanceof Class<?>) {
            location = ((Class<?>) source).getName();
            type = param;
            inputsource = source;
            if (sourceType == null) {
                sourceType = "MbeansDescriptorsIntrospectionSource";
            }
        }

        if (sourceType == null) {
            sourceType = "MbeansDescriptorsDigesterSource";
        }

        //获取建模资源对象
        ModelerSource ds = getModelerSource(sourceType);

        //使用建模资源对象 加载描述其
        List<ObjectName> mbeans = ds.loadDescriptors(this, type, inputsource);

        return mbeans;
    }


    /**
     * 向MBeanServer仓库中注册一个MBean对象 比如StandardServer对象
     * 根据bean对象的全类名查找出ManagedMBean对象  然后根据ManagedBean对象创建一个动态的MBean对象
     * 动态的MBean对象持有对ManagedMBean和bean对象的引用
     * 将动态的MBean对象注册到MBeanServer仓库
     * @param bean MBean对象
     * @param oname 比如Catalina:type=server
     * @param type 类型 MBean对象的全类名
     * @throws Exception
     */
    public void registerComponent(Object bean, ObjectName oname, String type) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Managed= " + oname);
        }

        if (bean == null) {//将要注册到MBeanServer仓库的MBean对象不能为null
            log.error("Null component " + oname );
            return;
        }

        try {
            if (type == null) {//如果指定全类名
                //获取MBean对象的全类名
                type = bean.getClass().getName();
            }

            //获取对象的ManagedBean对象
            ManagedBean managed = findManagedBean(null, bean.getClass(), type);

            //创建一个动态的MBean对象
            DynamicMBean mbean = managed.createMBean(bean);

            if (getMBeanServer().isRegistered(oname)) {
                if (log.isDebugEnabled()) {
                    log.debug("Unregistering existing component " + oname);
                }
                getMBeanServer().unregisterMBean(oname);
            }

            //将动态的mbean对象 注册到MBeanServer仓库  动态mbean对象 持有对bean以及managedBean的引用
            getMBeanServer().registerMBean(mbean, oname);
        } catch (Exception ex) {
            log.error("Error registering " + oname, ex );
            throw ex;
        }
    }


    /**
     * 加载描述器对象
     * @param packageName 包名
     * @param classLoader 类加载器对象
     */
    public void loadDescriptors(String packageName, ClassLoader classLoader) {
        //将包名中的.用/替换
        String res = packageName.replace('.', '/');

        if (log.isTraceEnabled()) {
            log.trace("Finding descriptor " + res);
        }

        //如果之前搜素过包路径  直接返回
        if (searchedPaths.get(packageName) != null) {
            return;
        }

        //描述器文件路径
        String descriptors = res + "/mbeans-descriptors.xml";
        //类加载器打开资源
        URL dURL = classLoader.getResource(descriptors);

        if (dURL == null) {//如果包下的mbeans-descriptors.xml文件不存在 直接返回
            return;
        }

        log.debug("Found " + dURL);
        //将包下的url路径放入缓存
        searchedPaths.put(packageName, dURL);
        try {
            load("MbeansDescriptorsDigesterSource", dURL, null);
        } catch(Exception ex ) {
            log.error("Error loading " + dURL);
        }
    }


    /**
     * Lookup the component descriptor in the package and in the parent
     * packages.
     */
    private void findDescriptor(Class<?> beanClass, String type) {
        if (type == null) {
            type = beanClass.getName();
        }
        ClassLoader classLoader = null;
        if (beanClass != null) {
            classLoader = beanClass.getClassLoader();
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = this.getClass().getClassLoader();
        }

        String className = type;
        String pkg = className;
        while (pkg.indexOf('.') > 0) {
            int lastComp = pkg.lastIndexOf('.');
            if (lastComp <= 0)
                return;
            pkg = pkg.substring(0, lastComp);
            if (searchedPaths.get(pkg) != null) {
                return;
            }
            loadDescriptors(pkg, classLoader);
        }
    }


    /**
     * 根据资源的类名 获取建模资源
     * @param type 建模资源类名
     * @return
     * @throws Exception
     */
    private ModelerSource getModelerSource(String type) throws Exception {
        if (type == null)
            type = "MbeansDescriptorsDigesterSource";
        if (!type.contains(".")) {
            type = "org.apache.tomcat.util.modeler.modules." + type;
        }

        //获取 MbeansDescriptorsDigesterSource类
        Class<?> c = Class.forName(type);

        //创建MbeansDescriptorsDigesterSource对象
        ModelerSource ds = (ModelerSource) c.getConstructor().newInstance();
        return ds;
    }


    // -------------------- Registration --------------------

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        synchronized (serverLock) {
            this.server = server;
        }
        return name;
    }


    @Override
    public void postRegister(Boolean registrationDone) {
    }


    @Override
    public void preDeregister() throws Exception {
    }


    @Override
    public void postDeregister() {
    }
}
