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


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * 解析Server/Service/Enginer/Host变迁的规则类
 */
@SuppressWarnings("deprecation")
public class HostRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 前缀
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public HostRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public HostRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加子标签解析规则
     * @param digester Digester instance to which the new Rule instances
     */
    @Override
    public void addRuleInstances(Digester digester) {

        //解析Server/Service/Enginer/Host标签
        //创建StandardHost对象  如果标签中的属性className指定了class全类名 使用用户指定的class全类名
        digester.addObjectCreate(prefix + "Host",
                                 "org.apache.catalina.core.StandardHost",
                                 "className");
        //设置StandardHost对象的属性
        digester.addSetProperties(prefix + "Host");

        //设置StandardHost容器对象的parentClassLoader为StandardEnginer容器对象的parentClassLoader
        digester.addRule(prefix + "Host",
                         new CopyParentClassLoaderRule());
        //给StandardHost容器对象添加一个HostConfig 生命周期监听器
        digester.addRule(prefix + "Host",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.HostConfig",
                          "hostConfigClass"));
        //将StandardHost对象作为参数 执行StandardEngine的addChild方法
        //将StandardHost作为子容器 添加StandardEngine父容器的子容器表
        digester.addSetNext(prefix + "Host",
                            "addChild",
                            "org.apache.catalina.Container");

        digester.addCallMethod(prefix + "Host/Alias",
                               "addAlias", 0);

        //Cluster configuration start
        digester.addObjectCreate(prefix + "Host/Cluster",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Host/Cluster");
        digester.addSetNext(prefix + "Host/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");
        //Cluster configuration end

        digester.addObjectCreate(prefix + "Host/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Host/Listener");
        digester.addSetNext(prefix + "Host/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addRuleSet(new RealmRuleSet(prefix + "Host/"));

        //创建AccessLogValve对象
        digester.addObjectCreate(prefix + "Host/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        //设置AccessLogValve对象的属性值
        digester.addSetProperties(prefix + "Host/Valve");
        //将AccessLogValve对象作为参数 执行StandardHost对象的addValve方法
        digester.addSetNext(prefix + "Host/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

    }


}
