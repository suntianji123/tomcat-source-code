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
package org.apache.catalina.mbeans;

import java.util.Set;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Group;
import org.apache.catalina.Loader;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.util.ContextName;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Public utility methods in support of the server side MBeans implementation.
 *
 * @author Craig R. McClanahan
 * @author Amy Roh
 */
public class MBeanUtils {

    // ------------------------------------------------------- Static Variables

    /**
     * 创建某个对象存储MBeanServer的索引名时候 如果对象的全类名为数组指定的类名 将使用固定的名字
     */
    private static final String exceptions[][] = {
        { "org.apache.catalina.users.MemoryGroup",
          "Group" },
        { "org.apache.catalina.users.MemoryRole",
          "Role" },
        { "org.apache.catalina.users.MemoryUser",
          "User" },
    };


    /**
     * 登记处对象
     */
    private static Registry registry = createRegistry();


    /**
     * 存储MBean的MBeanServer仓库对象
     */
    private static MBeanServer mserver = createServer();


    // --------------------------------------------------------- Static Methods

    /**
     * 创建存储到MBeanServer仓库中的索引名
     * @param component 将要被包装为动态的MBean对象
     * @return
     */
    static String createManagedName(Object component) {

        // 获取对象的全类名
        String className = component.getClass().getName();
        for (String[] exception : exceptions) {//如果是指定的全类名 将用固定的名字代替
            if (className.equals(exception[0])) {
                return exception[1];
            }
        }

        //获取最后一个.下标
        int period = className.lastIndexOf('.');
        if (period >= 0)
            className = className.substring(period + 1);
        //返回类名（不是全类名 ）
        return className;

    }


    /**
     * 将某个资源环境对象 包装为动态的MBean对象  存储到MBeanServer仓库
     * @param environment 环境资源对象
     * @return 对象被包装的动态的MBean对象
     * @throws Exception
     */
    public static DynamicMBean createMBean(ContextEnvironment environment)
        throws Exception {
        //获取对象在MBeanServer仓库中的索引名
        String mname = createManagedName(environment);
        //查找仓库中是否已经存在一个使用当前索引的MBean对象
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(environment);
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * 为资源创建动态的MBean对象
     * @param resource 资源对象
     * @return
     * @throws Exception
     */
    public static DynamicMBean createMBean(ContextResource resource)
        throws Exception {

        //获取对象的简写类名
        String mname = createManagedName(resource);
        //查找当前资源对象的ManagedBean对象
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {//不存在ManagedBean 说明当前资源对象 不能注册到MBeanServer仓库
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }

        //获取ManagedBean对象管理的领域 Catalina
        String domain = managed.getDomain();
        if (domain == null)//没有设置领域
            //默认的领域
            domain = mserver.getDefaultDomain();

        //为资源对象创建一个动态的MBean对象
        DynamicMBean mbean = managed.createMBean(resource);

        //创建将动态MBean对象存储到仓库的索引索引 比如Catalina:type=Resource,resourcetype=Global,class=org.apache.catalina.UserDatabase,name=UserDatabase
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered( oname ))  {//如果仓库中已经包含这个索引名的对象 将之前注册的MBean对象 取消注册
            mserver.unregisterMBean(oname);
        }

        //将资源对象包装为动态的MBean对象 注册到MBeanServer仓库
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * Create, register, and return an MBean for this
     * <code>ContextResourceLink</code> object.
     *
     * @param resourceLink The ContextResourceLink to be managed
     * @return a new MBean
     * @exception Exception if an MBean cannot be created or registered
     */
    public static DynamicMBean createMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(resourceLink);
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * Create, register, and return an MBean for this
     * <code>Group</code> object.
     *
     * @param group The Group to be managed
     * @return a new MBean
     * @exception Exception if an MBean cannot be created or registered
     */
    static DynamicMBean createMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(group);
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * Create, register, and return an MBean for this
     * <code>Role</code> object.
     *
     * @param role The Role to be managed
     * @return a new MBean
     * @exception Exception if an MBean cannot be created or registered
     */
    static DynamicMBean createMBean(Role role)
        throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(role);
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * Create, register, and return an MBean for this
     * <code>User</code> object.
     *
     * @param user The User to be managed
     * @return a new MBean
     * @exception Exception if an MBean cannot be created or registered
     */
    static DynamicMBean createMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        DynamicMBean mbean = managed.createMBean(user);
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * 创建动态的MBean对象
     * @param userDatabase 用户数据库对象
     * @return
     * @throws Exception
     */
    static DynamicMBean createMBean(UserDatabase userDatabase)
        throws Exception {

        //简写类名MemoryDatabase
        String mname = createManagedName(userDatabase);
        //查找ManagedBean对象
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        //获取领域
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        //创建动态的MBean对象
        DynamicMBean mbean = managed.createMBean(userDatabase);
        //生成动态的MBean对象的索引名
        ObjectName oname = createObjectName(domain, userDatabase);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }

        //将动态的MBean对象 注册到MBeanServer仓库
        mserver.registerMBean(mbean, oname);
        return mbean;

    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>Service</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param environment The ContextEnvironment to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    public static ObjectName createObjectName(String domain,
                                              ContextEnvironment environment)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Object container =
                environment.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=Environment" +
                        ",resourcetype=Global,name=" + environment.getName());
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=Environment" +
                        ",resourcetype=Context,host=" + host.getName() +
                        ",context=" + cn.getDisplayName() +
                        ",name=" + environment.getName());
        }
        return name;

    }


    /**
     * 创建资源对象存储到MBeanServer仓库后的索引名
     * @param domain 领域
     * @param resource 资源对象
     * @return
     * @throws MalformedObjectNameException
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResource resource)
        throws MalformedObjectNameException {

        //结果索引对象
        ObjectName name = null;
        String quotedResourceName = ObjectName.quote(resource.getName());

        //获取容器对象  比如StandardServer
        Object container =
                resource.getNamingResources().getContainer();
        if (container instanceof Server) {
            //索引名Catalina:type=Resource,resourcetype=Global,class=org.apache.catalina.UserDatabase,name=UserDatabase
            name = new ObjectName(domain + ":type=Resource" +
                    ",resourcetype=Global,class=" + resource.getType() +
                    ",name=" + quotedResourceName);
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=Resource" +
                    ",resourcetype=Context,host=" + host.getName() +
                    ",context=" + cn.getDisplayName() +
                    ",class=" + resource.getType() +
                    ",name=" + quotedResourceName);
        }

        return name;

    }


     /**
     * Create an <code>ObjectName</code> for this
     * <code>ContextResourceLink</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param resourceLink The ContextResourceLink to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResourceLink resourceLink)
        throws MalformedObjectNameException {

        ObjectName name = null;
        String quotedResourceLinkName
                = ObjectName.quote(resourceLink.getName());
        Object container =
                resourceLink.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=ResourceLink" +
                    ",resourcetype=Global" +
                    ",name=" + quotedResourceLinkName);
        } else if (container instanceof Context) {
            Context context = ((Context)container);
            ContextName cn = new ContextName(context.getName(), false);
            Container host = context.getParent();
            name = new ObjectName(domain + ":type=ResourceLink" +
                    ",resourcetype=Context,host=" + host.getName() +
                    ",context=" + cn.getDisplayName() +
                    ",name=" + quotedResourceLinkName);
        }

        return name;

    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>Group</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param group The Group to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    static ObjectName createObjectName(String domain,
                                              Group group)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Group,groupname=" +
                              ObjectName.quote(group.getGroupname()) +
                              ",database=" + group.getUserDatabase().getId());
        return name;

    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>Loader</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param loader The Loader to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    static ObjectName createObjectName(String domain, Loader loader)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Context context = loader.getContext();

        ContextName cn = new ContextName(context.getName(), false);
        Container host = context.getParent();
        name = new ObjectName(domain + ":type=Loader,host=" + host.getName() +
                ",context=" + cn.getDisplayName());

        return name;
    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>Role</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param role The Role to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    static ObjectName createObjectName(String domain, Role role)
            throws MalformedObjectNameException {

         ObjectName name = new ObjectName(domain + ":type=Role,rolename=" +
                 ObjectName.quote(role.getRolename()) +
                 ",database=" + role.getUserDatabase().getId());
        return name;
    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>User</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param user The User to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    static ObjectName createObjectName(String domain, User user)
            throws MalformedObjectNameException {

        ObjectName name = new ObjectName(domain + ":type=User,username=" +
                ObjectName.quote(user.getUsername()) +
                ",database=" + user.getUserDatabase().getId());
        return name;
    }


    /**
     * Create an <code>ObjectName</code> for this
     * <code>UserDatabase</code> object.
     *
     * @param domain Domain in which this name is to be created
     * @param userDatabase The UserDatabase to be named
     * @return a new object name
     * @exception MalformedObjectNameException if a name cannot be created
     */
    static ObjectName createObjectName(String domain,
                                              UserDatabase userDatabase)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=UserDatabase,database=" +
                              userDatabase.getId());
        return name;

    }

    /**
     * 解析包下的mbeans-descriptors.xml文件 创建MangedBean对象 并将ManagedBean对象  通过登记对象注册到MBeanServer仓库
     * @return
     */
    public static synchronized Registry createRegistry() {
        if (registry == null) {//登记处对象 不存在
            //创建访问MBeanServer的登记处对象
            registry = Registry.getRegistry(null, null);
            //获取当前对象的加载器
            ClassLoader cl = MBeanUtils.class.getClassLoader();

            //解析org/apache/catalina/mbeans/mbeans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.mbeans",  cl);

            //解析org/apache/catalina/authenticatorm/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.authenticator", cl);

            //解析org/apache/catalina/core/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.core", cl);

            //解析org/apache/catalina/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina", cl);

            //解析org/apache/catalina/deploy/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.deploy", cl);

            //解析org/apache/catalina/loader/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.loader", cl);

            //解析org/apache/catalina/realm/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.realm", cl);

            //解析org/apache/catalina/session/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.session", cl);

            //解析org/apache/catalina/startup/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.startup", cl);

            //解析org/apache/catalina/users/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.users", cl);

            //解析org/apache/catalina/ha/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.ha", cl);

            //解析org/apache/catalina/connector/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.connector", cl);

            //解析org/apache/catalina/valves/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.valves",  cl);

            //解析org/apache/catalina/storeconfig/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.catalina.storeconfig",  cl);

            //解析org/apache/catalina/web/beans-descriptors.xml文件 创建ManagedBean对象  并将ManagedBean对象 注册到MBeanServer仓库
            //登记org.apache.tomcat.util.descriptor.web.ContextEnvironment类对应的ManaagedBean对象 并将ManagedBean对象注册到MBeanServer仓库
            //登记org.apache.tomcat.util.descriptor.web.ContextResource类对应的ManaagedBean对象 并将ManagedBean对象注册到MBeanServer仓库
            //登记org.apache.tomcat.util.descriptor.web.ContextResourceLink类对应的ManaagedBean对象 并将ManagedBean对象注册到MBeanServer仓库
            registry.loadDescriptors("org.apache.tomcat.util.descriptor.web",  cl);
        }
        return registry;
    }


    /**
     * 创建MBeanServer存储MBean的仓库对象
     * @return
     */
    public static synchronized MBeanServer createServer() {
        if (mserver == null) {
            mserver = Registry.getRegistry(null, null).getMBeanServer();
        }
        return mserver;
    }


    /**
     * Deregister the MBean for this
     * <code>ContextEnvironment</code> object.
     *
     * @param environment The ContextEnvironment to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    public static void destroyMBean(ContextEnvironment environment)
        throws Exception {

        String mname = createManagedName(environment);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * Deregister the MBean for this
     * <code>ContextResource</code> object.
     *
     * @param resource The ContextResource to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    public static void destroyMBean(ContextResource resource)
        throws Exception {

        // If this is a user database resource need to destroy groups, roles,
        // users and UserDatabase mbean
        if ("org.apache.catalina.UserDatabase".equals(resource.getType())) {
            destroyMBeanUserDatabase(resource.getName());
        }

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered(oname ))
            mserver.unregisterMBean(oname);

    }


    /**
     * Deregister the MBean for this
     * <code>ContextResourceLink</code> object.
     *
     * @param resourceLink The ContextResourceLink to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    public static void destroyMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }

    /**
     * Deregister the MBean for this
     * <code>Group</code> object.
     *
     * @param group The Group to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    static void destroyMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * Deregister the MBean for this
     * <code>Role</code> object.
     *
     * @param role The Role to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    static void destroyMBean(Role role)
        throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * Deregister the MBean for this
     * <code>User</code> object.
     *
     * @param user The User to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    static void destroyMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * Deregister the MBean for the
     * <code>UserDatabase</code> object with this name.
     *
     * @param userDatabase The UserDatabase to be managed
     *
     * @exception Exception if an MBean cannot be deregistered
     */
    static void destroyMBeanUserDatabase(String userDatabase)
        throws Exception {

        ObjectName query = null;
        Set<ObjectName> results = null;

        // Groups
        query = new ObjectName(
                "Users:type=Group,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // Roles
        query = new ObjectName(
                "Users:type=Role,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // Users
        query = new ObjectName(
                "Users:type=User,database=" + userDatabase + ",*");
        results = mserver.queryNames(query, null);
        for(ObjectName result : results) {
            mserver.unregisterMBean(result);
        }

        // The database itself
        ObjectName db = new ObjectName(
                "Users:type=UserDatabase,database=" + userDatabase);
        if( mserver.isRegistered(db) ) {
            mserver.unregisterMBean(db);
        }
    }
}
