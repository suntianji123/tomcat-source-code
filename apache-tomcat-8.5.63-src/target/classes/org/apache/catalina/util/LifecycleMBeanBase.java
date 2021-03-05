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

package org.apache.catalina.util;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Globals;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

public abstract class LifecycleMBeanBase extends LifecycleBase
        implements JmxEnabled {

    private static final Log log = LogFactory.getLog(LifecycleMBeanBase.class);

    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /**
     * 对象的领域名 比如Catalina
     */
    private String domain = null;

    /**
     * 当前对象存储到MBeanServer仓库后 在MBeanServer中的索引名 比如StandardServer在MBeanServer中的索引名为Catalina:type=server
     */
    private ObjectName oname = null;

    /**
     * 储存ManagedBean对象的仓库对象
     */
    protected MBeanServer mserver = null;

    /**
     * 公共的初始化
     * @throws LifecycleException
     */
    @Override
    protected void initInternal() throws LifecycleException {
        // If oname is not null then registration has already happened via
        // preRegister().
        if (oname == null) {
            //获取存储bean对象的MBeanServer对象
            mserver = Registry.getRegistry(null, null).getMBeanServer();

            //将当前对象注册到MBeanServer仓库  返回当前在MBeanServer仓库中的索引名
            oname = register(this, getObjectNameKeyProperties());
        }
    }


    /**
     * Sub-classes wishing to perform additional clean-up should override this
     * method, ensuring that super.destroyInternal() is the last call in the
     * overriding method.
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        unregister(oname);
    }


    /**
     * Specify the domain under which this component should be registered. Used
     * with components that cannot (easily) navigate the component hierarchy to
     * determine the correct domain to use.
     */
    @Override
    public final void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * Obtain the domain under which this component will be / has been
     * registered.
     */
    @Override
    public final String getDomain() {
        if (domain == null) {//领域对象
            domain = getDomainInternal();
        }

        if (domain == null) {
            domain = Globals.DEFAULT_MBEAN_DOMAIN;
        }

        return domain;
    }


    /**
     * Method implemented by sub-classes to identify the domain in which MBeans
     * should be registered.
     *
     * @return  The name of the domain to use to register MBeans.
     */
    protected abstract String getDomainInternal();


    /**
     * 获取当前对象注册到MBeanServer仓库后的索引名
     * @return
     */
    @Override
    public final ObjectName getObjectName() {
        return oname;
    }


    /**
     * 将当前包装为动态的MBean对象后 注册到MBeanServer仓库的索引名
     * @return
     */
    protected abstract String getObjectNameKeyProperties();


    /**
     * 将当前对象 注册到MBeanServer仓库 放回对象的名字
     * @param obj 需要注册到MBeanServer仓库的对象
     * @param objectNameKeyProperties 对象的名字属性值字符串
     * @return 返回当前对象在MBeanServer仓库中的索引名
     */
    protected final ObjectName register(Object obj,
            String objectNameKeyProperties) {

        //获取领域名 比如Server/Service/Engine name属性值Catalina
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        //Catalina:type=server
        name.append(objectNameKeyProperties);

        ObjectName on = null;

        try {
            on = new ObjectName(name.toString());

            //找出当前对象比如StandardServer的ManagedMBean对象
            //ManaagedMBean创建 一个动态的MBean对象 持有对StandardServer对象以及ManagedMBean对象的引用
            //将动态的MBean对象注册到MBeanServer仓库
            Registry.getRegistry(null, null).registerComponent(obj, on, null);
        } catch (Exception e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name), e);
        }

        return on;
    }


    /**
     * Utility method to enable sub-classes to easily unregister additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param on    The name of the component to unregister
     */
    protected final void unregister(ObjectName on) {

        // If null ObjectName, just return without complaint
        if (on == null) {
            return;
        }

        // If the MBeanServer is null, log a warning & return
        if (mserver == null) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterNoServer", on));
            return;
        }

        try {
            mserver.unregisterMBean(on);
        } catch (MBeanRegistrationException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        } catch (InstanceNotFoundException e) {
            log.warn(sm.getString("lifecycleMBeanBase.unregisterFail", on), e);
        }

    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postDeregister() {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postRegister(Boolean registrationDone) {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void preDeregister() throws Exception {
        // NOOP
    }


    /**
     * Allows the object to be registered with an alternative
     * {@link MBeanServer} and/or {@link ObjectName}.
     */
    @Override
    public final ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {

        this.mserver = server;
        this.oname = name;
        this.domain = name.getDomain().intern();

        return oname;
    }

}
