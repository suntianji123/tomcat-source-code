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


import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;

import org.apache.catalina.Group;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;


/**
 * 全局资源监听器类
 */
public class GlobalResourcesLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(GlobalResourcesLifecycleListener.class);


    // ----------------------------------------------------- Instance Variables

    /**
     *组件 StandardServer对象
     */
    protected Lifecycle component = null;

    /**
     * 登记处对象
     */
    protected static final Registry registry = MBeanUtils.createRegistry();


    // ---------------------------------------------- LifecycleListener Methods

    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.START_EVENT.equals(event.getType())) {//开始事件
            //设置组件为StandardServer对象
            component = event.getLifecycle();
            createMBeans();
        } else if (Lifecycle.STOP_EVENT.equals(event.getType())) {//结束事件
            destroyMBeans();
            component = null;
        }
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 从上下文Context中根据匹配路径 / 获取之前创建的namingContext对象
     * 获取注册到namingContext对象中的UserDatabase的引用对象  生产一个实例MemoryDatabase
     * 将MemoryData包装为动态的MBean对象 存储到MBeanServer仓库
     */
    protected void createMBeans() {
        // Look up our global naming context
        Context context = null;
        try {
            //通过jvm参数 java.naming.factory.initial指定的factory类 实例化一个SelectorContext Context选择器对象
            //SelectorContext持有环境标的引用
            //调用 SelectorContext的lookup方法  以当前线程的类加载器为key 获取类加载器的NamingContext对象
            context = (Context) (new InitialContext()).lookup("java:/");
        } catch (NamingException e) {
            log.error("No global naming context defined for server");
            return;
        }

        // Recurse through the defined global JNDI resources context
        try {
            //获取注册到namingContext的UserDatabase的引用 实例化一个MemoryDatabase对象
            //将MemoryDatabase对象 包装为动态的MBean 存储到MBeanServer仓库
            createMBeans("", context);
        } catch (NamingException e) {
            log.error("Exception processing Global JNDI Resources", e);
        }
    }


    /**
     * 创建MBean对象
     * @param prefix 前缀
     * @param context
     * @throws NamingException
     */
    protected void createMBeans(String prefix, Context context) throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug("Creating MBeans for Global JNDI Resources in Context '" +
                prefix + "'");
        }

        try {
            //获取所有注册到上下文中的对象迭代器
            NamingEnumeration<Binding> bindings = context.listBindings("");
            while (bindings.hasMore()) {//有一个实体NamingEntry UserDatabase对应的ResourceRef对象
                //获取绑定对象
                Binding binding = bindings.next();
                //获取绑定名
                String name = prefix + binding.getName();
                //获取绑定对象
                Object value = context.lookup(binding.getName());
                if (log.isDebugEnabled()) {
                    log.debug("Checking resource " + name);
                }
                if (value instanceof Context) {
                    createMBeans(name + "/", (Context) value);
                } else if (value instanceof UserDatabase) {
                    try {
                        //将UserDatabase注册MBeanServer服务器
                        createMBeans(name, (UserDatabase) value);
                    } catch (Exception e) {
                        log.error("Exception creating UserDatabase MBeans for " + name, e);
                    }
                }
            }
        } catch( RuntimeException ex) {
            log.error("RuntimeException " + ex);
        } catch( OperationNotSupportedException ex) {
            log.error("Operation not supported " + ex);
        }
    }


    /**
     * 创建UserDatabase对象的MBean对象
     * @param name UserDatabase名
     * @param database 对象
     * @throws Exception
     */
    protected void createMBeans(String name, UserDatabase database) throws Exception {

        // Create the MBean for the UserDatabase itself
        if (log.isDebugEnabled()) {
            log.debug("Creating UserDatabase MBeans for resource " + name);
            log.debug("Database=" + database);
        }
        try {

            //将database对象包装为动态的MBean对象 存储到MBeanServer仓库
            MBeanUtils.createMBean(database);
        } catch(Exception e) {
            throw new IllegalArgumentException(
                    "Cannot create UserDatabase MBean for resource " + name, e);
        }

        //将对象的角色注册到MBeanServer仓库
        Iterator<Role> roles = database.getRoles();
        while (roles.hasNext()) {
            Role role = roles.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating Role MBean for role " + role);
            }
            try {
                MBeanUtils.createMBean(role);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot create Role MBean for role " + role, e);
            }
        }

        // 将数据库的组注册到MBeanServer仓库
        Iterator<Group> groups = database.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating Group MBean for group " + group);
            }
            try {
                MBeanUtils.createMBean(group);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create Group MBean for group " + group, e);
            }
        }

        // 将数据库所有的用户注册到MBeanServer仓库
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            if (log.isDebugEnabled()) {
                log.debug("  Creating User MBean for user " + user);
            }
            try {
                MBeanUtils.createMBean(user);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot create User MBean for user " + user, e);
            }
        }
    }


    /**
     * Destroy the MBeans for the interesting global JNDI resources.
     */
    protected void destroyMBeans() {
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBeans for Global JNDI Resources");
        }
    }
}
